package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    void adPositionWritesL2AndEvicts() {
        cache.put(CacheNamespace.AD_POSITION, "home", "payload");
        assertThat(store.get("ad:position:home")).isNotNull();
        AtomicInteger loads = new AtomicInteger();
        String cached = cache.get(CacheNamespace.AD_POSITION, "home", String.class, () -> {
            loads.incrementAndGet();
            return "live";
        });
        assertThat(cached).isEqualTo("payload");
        cache.evict(CacheNamespace.AD_POSITION, "home");
        String after = cache.get(CacheNamespace.AD_POSITION, "home", String.class, () -> {
            loads.incrementAndGet();
            return "live";
        });
        assertThat(after).isEqualTo("live");
        cache.evictNamespace(CacheNamespace.AD_POSITION);
        assertThat(store.get("ad:position:home")).isNull();
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
    void l1ExpiresAfterNamespaceTtl() {
        AtomicLong nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        cache = new TwoLevelPlatformCache(store, ticker);
        cache.put(CacheNamespace.TASK_PUBLISHED_INDEX, "all", "v1");
        store.unlink("task:published-index:all");
        assertThat(cache.get(CacheNamespace.TASK_PUBLISHED_INDEX, "all", String.class, () -> "miss"))
                .isEqualTo("v1");
        nanos.addAndGet(Duration.ofSeconds(31).toNanos());
        AtomicInteger loads = new AtomicInteger();
        String after = cache.get(CacheNamespace.TASK_PUBLISHED_INDEX, "all", String.class, () -> {
            loads.incrementAndGet();
            return "v2";
        });
        assertThat(after).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void l2DownFallsBackToLoader() {
        cache.put(CacheNamespace.DICT, "x", "old");
        store.setAvailable(false);
        String value = cache.get(CacheNamespace.DICT, "fresh", String.class, () -> "db");
        assertThat(value).isEqualTo("db");
    }
}
