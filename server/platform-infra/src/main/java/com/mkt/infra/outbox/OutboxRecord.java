package com.mkt.infra.outbox;

import java.time.Instant;

public record OutboxRecord(
        long id,
        String eventCode,
        String producer,
        String aggregateType,
        String aggregateId,
        String payload,
        String status,
        int retryCount,
        Instant nextRetryAt,
        Instant createdAt) {
}
