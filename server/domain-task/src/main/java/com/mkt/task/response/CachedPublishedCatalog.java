package com.mkt.task.response;

import java.util.List;

/**
 * Portal list catalog under {@code task:published-index:all}.
 * Metadata only — snapshot JSON lives in {@code task:snapshot} L1/L2 so the index
 * does not duplicate ~1MB of snapshot bodies in the same process (list-under-p1 mem).
 * Publish still evicts this key; TTL unchanged in {@link com.mkt.infra.cache.CacheNamespace}.
 */
public record CachedPublishedCatalog(List<Entry> entries) {

    /** Published task pointer; snapshot body resolved via TASK_SNAPSHOT / DB. */
    public record Entry(long id, String status, int version) {}
}
