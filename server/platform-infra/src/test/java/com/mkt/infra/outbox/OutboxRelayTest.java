package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
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
}
