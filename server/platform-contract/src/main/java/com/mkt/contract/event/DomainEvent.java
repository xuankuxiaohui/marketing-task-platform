package com.mkt.contract.event;

import java.util.Objects;

/**
 * Outbox event envelope. Matches {@code EventPublisher.append(eventCode, aggregateType,
 * aggregateId, payload)} (design §6.4).
 */
public record DomainEvent(String eventCode, String aggregateType, String aggregateId, Object payload) {

    public DomainEvent {
        Objects.requireNonNull(eventCode, "eventCode");
        EventCodes.requireKnown(eventCode);
        Objects.requireNonNull(aggregateType, "aggregateType");
        if (aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType is blank");
        }
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId is blank");
        }
    }
}
