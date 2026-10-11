package com.mkt.task.response;

import java.util.List;

/**
 * Portal list catalog under {@code task:published-index:all} (TTL 30s).
 * Embeds snapshot JSON so list avoids N× DB snapshot round-trips on cache hit.
 */
public record CachedPublishedCatalog(List<Entry> entries) {

    public record Entry(long id, String status, int version, String snapshotJson) {}
}
