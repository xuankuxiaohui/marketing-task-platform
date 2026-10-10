package com.mkt.infra.outbox;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dual-app relay; SELECT always filters {@code producer=:self} (D-11 / design §6.4).
 *
 * <p>DEC-002: one lock hold may drain multiple batches (default 100) until the queue is empty or the
 * lock-hold budget (default 2s) is exhausted; schedule remains fixedDelay 5s. No MQ.
 */
public final class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** DEC-002 starter batch size. */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** DEC-002 starter single lock-hold budget. */
    public static final Duration DEFAULT_LOCK_HOLD_BUDGET = Duration.ofSeconds(2);

    private final OutboxStore store;
    private final OutboxProducer producer;
    private final PlatformLock locks;
    private final Clock clock;
    private final List<EventConsumer> consumers;
    private final int batchSize;
    private final Duration lockHoldBudget;

    public OutboxRelay(
            OutboxStore store,
            OutboxProducer producer,
            PlatformLock locks,
            Clock clock,
            List<EventConsumer> consumers) {
        this(store, producer, locks, clock, consumers, DEFAULT_BATCH_SIZE, DEFAULT_LOCK_HOLD_BUDGET);
    }

    public OutboxRelay(
            OutboxStore store,
            OutboxProducer producer,
            PlatformLock locks,
            Clock clock,
            List<EventConsumer> consumers,
            int batchSize,
            Duration lockHoldBudget) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }
        this.store = store;
        this.producer = producer;
        this.locks = locks;
        this.clock = clock;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
        this.batchSize = batchSize;
        this.lockHoldBudget = Objects.requireNonNull(lockHoldBudget, "lockHoldBudget");
    }

    public void tick() {
        String lockKey = LockKeys.outboxRelay(producer.id());
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        try {
            long startedNanos = System.nanoTime();
            long budgetNanos = lockHoldBudget.toNanos();
            while (true) {
                Instant now = clock.instant();
                List<OutboxRecord> batch = store.claimBatch(producer.id(), now, batchSize);
                if (batch.isEmpty()) {
                    return;
                }
                for (OutboxRecord row : batch) {
                    dispatch(row, now);
                }
                if (System.nanoTime() - startedNanos >= budgetNanos) {
                    return;
                }
            }
        } finally {
            locks.unlock(lockKey);
        }
    }

    private void dispatch(OutboxRecord row, Instant now) {
        List<ConsumerDirection> declared = OutboxRoutes.directions(row.eventCode());
        if (declared.isEmpty()) {
            log.warn("outbox route empty, deleting id={} code={}", row.id(), row.eventCode());
            store.delete(row.id());
            return;
        }
        List<EventConsumer> matched = new ArrayList<>();
        for (ConsumerDirection direction : declared) {
            EventConsumer found = find(direction);
            if (found == null) {
                log.error("outbox.missing.consumer code={} direction={} id={}", row.eventCode(), direction, row.id());
                fail(row, now);
                return;
            }
            matched.add(found);
        }
        try {
            for (EventConsumer consumer : matched) {
                consumer.consume(row);
            }
            store.delete(row.id());
        } catch (RuntimeException ex) {
            log.warn("outbox consumer failed id={} code={}", row.id(), row.eventCode(), ex);
            fail(row, now);
        }
    }

    private EventConsumer find(ConsumerDirection direction) {
        for (EventConsumer consumer : consumers) {
            if (consumer.direction() == direction) {
                return consumer;
            }
        }
        return null;
    }

    private void fail(OutboxRecord row, Instant now) {
        int next = row.retryCount() + 1;
        if (OutboxBackoff.dead(next)) {
            store.markDead(row.id(), next);
            return;
        }
        store.markRetry(row.id(), next, now.plus(OutboxBackoff.delayAfterFailure(next)));
    }
}
