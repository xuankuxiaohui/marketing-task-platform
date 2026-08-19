package com.mkt.infra.cache;

import java.util.function.Supplier;

/**
 * Business cache facade (design §6.2). Implementations are Caffeine L1 + Redis L2 +
 * {@code PUBLISH cache:evict}.
 */
public interface PlatformCache {

    <T> T get(CacheNamespace namespace, String bizKey, Class<T> type, Supplier<T> loader);

    void put(CacheNamespace namespace, String bizKey, Object value);

    void evict(CacheNamespace namespace, String bizKey);

    void evictPrefix(CacheNamespace namespace, String prefix);

    void evictNamespace(CacheNamespace namespace);

    void evictAfterCommit(CacheNamespace namespace, String bizKey);

    CacheStatsView stats(CacheNamespace namespace);
}
