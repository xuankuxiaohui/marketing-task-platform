package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TwoLevelPlatformCacheTest {

    private MemoryKeyValueStore store;
    private TwoLevelPlatformCache cache;

    @BeforeEach
    void setUp() {
        store = new MemoryKeyValueStore();
        cache = new TwoLevelPlatformCache(store);
    }

    @Test
    void getPutsL1AndL2ThenHits() {
        AtomicInteger loads = new AtomicInteger();
        String first = cache.get(CacheNamespace.DICT, "province", String.class, () -> {
            loads.incrementAndGet();
            return "GD";
        });
        String second = cache.get(CacheNamespace.DICT, "province", String.class, () -> {
            loads.incrementAndGet();
            return "other";
        });
        assertThat(first).isEqualTo("GD");
        assertThat(second).isEqualTo("GD");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(store.get("dict:province")).contains("GD");
    }

    @Test
    void evictBroadcastClearsPeerL1() {
        TwoLevelPlatformCache peer = new TwoLevelPlatformCache(store);
        cache.put(CacheNamespace.CONFIG, "k", "v1");
        assertThat(peer.get(CacheNamespace.CONFIG, "k", String.class, () -> "miss")).isEqualTo("v1");
        cache.evict(CacheNamespace.CONFIG, "k");
        AtomicInteger loads = new AtomicInteger();
        String after = peer.get(CacheNamespace.CONFIG, "k", String.class, () -> {
            loads.incrementAndGet();
            return "v2";
        });
        assertThat(after).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void sessionEvictIsForbidden() {
        assertThatThrownBy(() -> cache.evict(CacheNamespace.IDENTITY_SESSION, "token"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode().code())
                .isEqualTo("system.cache.session-forbidden");
        assertThatThrownBy(() -> cache.evictNamespace(CacheNamespace.IDENTITY_SESSION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cache.evictPrefix(CacheNamespace.IDENTITY_SESSION, "x"))
                .isInstanceOf(BusinessException.class);
        assertThat(cache.stats(CacheNamespace.IDENTITY_SESSION).keyCount()).isEqualTo("N/A");
    }

    @Test
    void adPositionIsPlaceholderNoL2() {
        cache.put(CacheNamespace.AD_POSITION, "home", "payload");
        assertThat(store.get("ad:position:home")).isNull();
        AtomicInteger loads = new AtomicInteger();
        String value = cache.get(CacheNamespace.AD_POSITION, "home", String.class, () -> {
            loads.incrementAndGet();
            return "live";
        });
        assertThat(value).isEqualTo("live");
        cache.evict(CacheNamespace.AD_POSITION, "home");
        cache.evictNamespace(CacheNamespace.AD_POSITION);
        assertThat(cache.stats(CacheNamespace.AD_POSITION).keyCount()).isEqualTo("0");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void evictAfterCommitRunsWhenNoTransaction() {
        cache.put(CacheNamespace.DICT, "a", "1");
        cache.evictAfterCommit(CacheNamespace.DICT, "a");
        assertThat(store.get("dict:a")).isNull();
    }

    @Test
    void evictAfterCommitWaitsForCommit() {
        cache.put(CacheNamespace.DICT, "a", "1");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.evictAfterCommit(CacheNamespace.DICT, "a");
            assertThat(store.get("dict:a")).isNotNull();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(store.get("dict:a")).isNull();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void l2DownFallsBackToLoader() {
        cache.put(CacheNamespace.DICT, "x", "old");
        store.setAvailable(false);
        String value = cache.get(CacheNamespace.DICT, "fresh", String.class, () -> "db");
        assertThat(value).isEqualTo("db");
    }
}
