package com.mkt.infra.cache;

/** Stats row for /admin/system/cache/stats (R9.1). */
public record CacheStatsView(String namespace, String keyCount, String hitRate) {

    public static CacheStatsView na(String namespace) {
        return new CacheStatsView(namespace, "N/A", "N/A");
    }

    public static CacheStatsView zeros(String namespace) {
        return new CacheStatsView(namespace, "0", "N/A");
    }
}
