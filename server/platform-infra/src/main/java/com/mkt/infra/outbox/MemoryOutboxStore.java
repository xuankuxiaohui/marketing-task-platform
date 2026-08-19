package com.mkt.infra.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory store for unit tests. */
public final class MemoryOutboxStore implements OutboxStore {

    private final AtomicLong seq = new AtomicLong();
    private final ConcurrentHashMap<Long, OutboxRecord> rows = new ConcurrentHashMap<>();
    private final java.time.Clock clock;

    public MemoryOutboxStore() {
        this(java.time.Clock.systemUTC());
    }

    public MemoryOutboxStore(java.time.Clock clock) {
        this.clock = clock;
    }

    @Override
    public long insert(String eventCode, String producer, String aggregateType, String aggregateId, String payload) {
        long id = seq.incrementAndGet();
        rows.put(
                id,
                new OutboxRecord(
                        id, eventCode, producer, aggregateType, aggregateId, payload, "PENDING", 0, null, clock.instant()));
        return id;
    }

    @Override
    public List<OutboxRecord> claimBatch(String producer, Instant now, int limit) {
        return rows.values().stream()
                .filter(r -> producer.equals(r.producer()) && "PENDING".equals(r.status()))
                .filter(r -> r.nextRetryAt() == null || !r.nextRetryAt().isAfter(now))
                .sorted(Comparator.comparingLong(OutboxRecord::id))
                .limit(limit)
                .toList();
    }

    @Override
    public void delete(long id) {
        rows.remove(id);
    }

    @Override
    public void markRetry(long id, int retryCount, Instant nextRetryAt) {
        rows.computeIfPresent(id, (k, r) -> new OutboxRecord(
                r.id(),
                r.eventCode(),
                r.producer(),
                r.aggregateType(),
                r.aggregateId(),
                r.payload(),
                r.status(),
                retryCount,
                nextRetryAt,
                r.createdAt()));
    }

    @Override
    public void markDead(long id, int retryCount) {
        rows.computeIfPresent(id, (k, r) -> new OutboxRecord(
                r.id(),
                r.eventCode(),
                r.producer(),
                r.aggregateType(),
                r.aggregateId(),
                r.payload(),
                "DEAD",
                retryCount,
                r.nextRetryAt(),
                r.createdAt()));
    }

    @Override
    public int countPending(String producer) {
        return (int) rows.values().stream()
                .filter(r -> producer.equals(r.producer()) && "PENDING".equals(r.status()))
                .count();
    }

    public OutboxRecord get(long id) {
        return rows.get(id);
    }

    public List<OutboxRecord> all() {
        return new ArrayList<>(rows.values());
    }
}
