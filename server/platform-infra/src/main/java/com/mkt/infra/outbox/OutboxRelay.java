package com.mkt.infra.outbox;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dual-app relay; SELECT always filters {@code producer=:self} (D-11 / design §6.4). */
public final class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private final OutboxStore store;
    private final OutboxProducer producer;
    private final PlatformLock locks;
    private final Clock clock;
    private final List<EventConsumer> consumers;

    public OutboxRelay(
            OutboxStore store,
            OutboxProducer producer,
            PlatformLock locks,
            Clock clock,
            List<EventConsumer> consumers) {
        this.store = store;
        this.producer = producer;
        this.locks = locks;
        this.clock = clock;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
    }

    public void tick() {
        String lockKey = LockKeys.outboxRelay(producer.id());
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        try {
            Instant now = clock.instant();
            for (OutboxRecord row : store.claimBatch(producer.id(), now, BATCH)) {
                dispatch(row, now);
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
