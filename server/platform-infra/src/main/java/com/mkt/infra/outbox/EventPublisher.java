package com.mkt.infra.outbox;

import com.mkt.kernel.json.JsonUtil;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Same-transaction outbox write (design §6.4). No current transaction → IllegalStateException.
 */
public final class EventPublisher {

    private final OutboxStore store;
    private final OutboxProducer producer;

    public EventPublisher(OutboxStore store, OutboxProducer producer) {
        this.store = store;
        this.producer = producer;
    }

    public long append(String eventCode, String aggregateType, String aggregateId, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("EventPublisher.append requires an active transaction");
        }
        if (eventCode == null || eventCode.isBlank()) {
            throw new IllegalArgumentException("eventCode is blank");
        }
        String json = payload == null ? "null" : JsonUtil.toJson(payload);
        return store.insert(eventCode, producer.id(), aggregateType, aggregateId, json);
    }

    public OutboxProducer producer() {
        return producer;
    }
}
