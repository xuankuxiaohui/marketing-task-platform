package com.mkt.infra.outbox;

import java.time.Instant;
import java.util.List;

public interface OutboxStore {

    long insert(String eventCode, String producer, String aggregateType, String aggregateId, String payload);

    List<OutboxRecord> claimBatch(String producer, Instant now, int limit);

    void delete(long id);

    void markRetry(long id, int retryCount, Instant nextRetryAt);

    void markDead(long id, int retryCount);

    int countPending(String producer);
}
