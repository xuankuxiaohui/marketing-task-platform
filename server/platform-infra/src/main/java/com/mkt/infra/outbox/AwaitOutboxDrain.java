package com.mkt.infra.outbox;

import java.time.Duration;
import java.time.Instant;

/** Test fixture: tick relay until no PENDING rows for this producer. */
public final class AwaitOutboxDrain {

    private final OutboxStore store;
    private final OutboxProducer producer;
    private final OutboxRelay relay;
    private final java.time.Clock clock;

    public AwaitOutboxDrain(OutboxStore store, OutboxProducer producer, OutboxRelay relay) {
        this(store, producer, relay, java.time.Clock.systemUTC());
    }

    public AwaitOutboxDrain(OutboxStore store, OutboxProducer producer, OutboxRelay relay, java.time.Clock clock) {
        this.store = store;
        this.producer = producer;
        this.relay = relay;
        this.clock = clock;
    }

    public void awaitDrain(Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        while (clock.instant().isBefore(deadline)) {
            if (store.countPending(producer.id()) == 0) {
                return;
            }
            relay.tick();
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("awaitOutboxDrain interrupted", ex);
            }
        }
        throw new IllegalStateException(
                "outbox not drained for " + producer.id() + " pending=" + store.countPending(producer.id()));
    }
}
