package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {

    private final MemoryOutboxStore store = new MemoryOutboxStore();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void onlyScansOwnProducer() {
        store.insert(OutboxRoutes.TASK_INSTANCE_START, "admin", "t", "1", "{}");
        store.insert(OutboxRoutes.TASK_INSTANCE_START, "portal", "t", "2", "{}");
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        relay(OutboxProducer.ADMIN, List.of(evt)).tick();
        assertThat(evt.ids).containsExactly(1L);
        assertThat(store.countPending("portal")).isEqualTo(1);
        assertThat(store.countPending("admin")).isZero();
    }

    @Test
    void missingDeclaredConsumerDoesNotDelete() {
        store.insert(OutboxRoutes.TASK_INSTANCE_COMPLETE, "admin", "t", "1", "{}");
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        relay(OutboxProducer.ADMIN, List.of(evt)).tick();
        assertThat(store.get(1L).status()).isEqualTo("PENDING");
        assertThat(store.get(1L).retryCount()).isEqualTo(1);
        assertThat(evt.ids).isEmpty();
    }

    @Test
    void unknownRouteDeletes() {
        store.insert("probe.none", "admin", "t", "1", "{}");
        relay(OutboxProducer.ADMIN, List.of()).tick();
        assertThat(store.get(1L)).isNull();
    }

    @Test
    void consumerFailureRetriesThenDead() {
        store.insert(OutboxRoutes.TASK_INSTANCE_START, "admin", "t", "1", "{}");
        EventConsumer boom = new EventConsumer() {
            @Override
            public ConsumerDirection direction() {
                return ConsumerDirection.EVT_EVENT_LOG;
            }

            @Override
            public void consume(OutboxRecord row) {
                throw new IllegalStateException("boom");
            }
        };
        OutboxRelay relay = relay(OutboxProducer.ADMIN, List.of(boom));
        for (int i = 0; i < 5; i++) {
            store.markRetry(1L, store.get(1L).retryCount(), null);
            relay.tick();
        }
        assertThat(store.get(1L).status()).isEqualTo("DEAD");
        assertThat(store.get(1L).retryCount()).isEqualTo(5);
    }

    @Test
    void successDeletesAndDrainHelper() {
        store.insert(OutboxRoutes.TASK_INSTANCE_START, "admin", "t", "1", "{}");
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        OutboxRelay relay = relay(OutboxProducer.ADMIN, List.of(evt));
        new AwaitOutboxDrain(store, OutboxProducer.ADMIN, relay).awaitDrain(java.time.Duration.ofSeconds(2));
        assertThat(store.countPending("admin")).isZero();
        assertThat(evt.calls.get()).isEqualTo(1);
    }

    @Test
    void multiBatchDrainsBeyondSingleBatchInOneTick() {
        int total = 250;
        for (int i = 0; i < total; i++) {
            store.insert(OutboxRoutes.TASK_INSTANCE_START, "admin", "t", String.valueOf(i), "{}");
        }
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        CountingStore counting = new CountingStore(store);
        OutboxRelay relay = new OutboxRelay(
                counting,
                OutboxProducer.ADMIN,
                new PlatformLock(new MemoryKeyValueStore()),
                clock,
                List.of(evt),
                100,
                Duration.ofSeconds(30));
        relay.tick();
        assertThat(evt.calls.get()).isEqualTo(total);
        assertThat(store.countPending("admin")).isZero();
        assertThat(counting.claimCalls.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void budgetExhaustedYieldsAfterBatch() {
        int total = 250;
        for (int i = 0; i < total; i++) {
            store.insert(OutboxRoutes.TASK_INSTANCE_START, "admin", "t", String.valueOf(i), "{}");
        }
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        OutboxRelay relay = new OutboxRelay(
                store,
                OutboxProducer.ADMIN,
                new PlatformLock(new MemoryKeyValueStore()),
                clock,
                List.of(evt),
                100,
                Duration.ZERO);
        relay.tick();
        assertThat(evt.calls.get()).isEqualTo(100);
        assertThat(store.countPending("admin")).isEqualTo(150);
        relay.tick();
        assertThat(evt.calls.get()).isEqualTo(200);
        assertThat(store.countPending("admin")).isEqualTo(50);
    }

    @Test
    void emptyQueueExitsWithoutConsume() {
        RecordingConsumer evt = new RecordingConsumer(ConsumerDirection.EVT_EVENT_LOG);
        CountingStore counting = new CountingStore(store);
        OutboxRelay relay = new OutboxRelay(
                counting,
                OutboxProducer.ADMIN,
                new PlatformLock(new MemoryKeyValueStore()),
                clock,
                List.of(evt),
                100,
                Duration.ofSeconds(2));
        relay.tick();
        assertThat(evt.calls.get()).isZero();
        assertThat(counting.claimCalls.get()).isEqualTo(1);
        assertThat(store.countPending("admin")).isZero();
    }

    private OutboxRelay relay(OutboxProducer producer, List<EventConsumer> consumers) {
        return new OutboxRelay(store, producer, new PlatformLock(new MemoryKeyValueStore()), clock, consumers);
    }

    private static final class RecordingConsumer implements EventConsumer {
        private final ConsumerDirection direction;
        private final List<Long> ids = new ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingConsumer(ConsumerDirection direction) {
            this.direction = direction;
        }

        @Override
        public ConsumerDirection direction() {
            return direction;
        }

        @Override
        public void consume(OutboxRecord row) {
            calls.incrementAndGet();
            ids.add(row.id());
        }
    }

    /** Delegates to MemoryOutboxStore while counting claimBatch invocations. */
    private static final class CountingStore implements OutboxStore {
        private final MemoryOutboxStore inner;
        private final AtomicInteger claimCalls = new AtomicInteger();

        private CountingStore(MemoryOutboxStore inner) {
            this.inner = inner;
        }

        @Override
        public long insert(String eventCode, String producer, String aggregateType, String aggregateId, String payload) {
            return inner.insert(eventCode, producer, aggregateType, aggregateId, payload);
        }

        @Override
        public List<OutboxRecord> claimBatch(String producer, Instant now, int limit) {
            claimCalls.incrementAndGet();
            return inner.claimBatch(producer, now, limit);
        }

        @Override
        public void delete(long id) {
            inner.delete(id);
        }

        @Override
        public void markRetry(long id, int retryCount, Instant nextRetryAt) {
            inner.markRetry(id, retryCount, nextRetryAt);
        }

        @Override
        public void markDead(long id, int retryCount) {
            inner.markDead(id, retryCount);
        }

        @Override
        public int countPending(String producer) {
            return inner.countPending(producer);
        }
    }
}
