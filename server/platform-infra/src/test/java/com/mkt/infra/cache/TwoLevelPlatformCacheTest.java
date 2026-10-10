package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    void putAfterCommitRunsWhenNoTransaction() {
        cache.putAfterCommit(CacheNamespace.TASK_SNAPSHOT, "9:1", "{\"v\":1}");
        assertThat(store.get("task:snapshot:9:1")).isNotNull();
    }

    @Test
    void putAfterCommitWaitsForCommitAndSkipsOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.putAfterCommit(CacheNamespace.TASK_SNAPSHOT, "9:1", "{\"v\":1}");
            assertThat(store.get("task:snapshot:9:1")).isNull();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(store.get("task:snapshot:9:1")).isNotNull();
        } finally {
            TransactionSynchronizationManager.clear();
        }

        store.unlink("task:snapshot:9:1");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.putAfterCommit(CacheNamespace.TASK_SNAPSHOT, "9:1", "{\"v\":1}");
            assertThat(store.get("task:snapshot:9:1")).isNull();
            // rollback path: afterCommit is never invoked
            TransactionSynchronizationManager.clear();
            assertThat(store.get("task:snapshot:9:1")).isNull();
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clear();
            }
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

    @Test
    void concurrentSameKeyGetCoalescesToSingleLoader() throws Exception {
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> cache.get(CacheNamespace.DICT, "herd", String.class, () -> {
                    int n = loads.incrementAndGet();
                    if (n == 1) {
                        loaderEntered.countDown();
                        awaitLatch(releaseLoader);
                    }
                    return "coalesced";
                })));
            }
            assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
            // Let waiters attach to the in-flight future before completing the loader.
            Thread.sleep(150);
            releaseLoader.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("coalesced");
            }
            assertThat(loads.get()).isEqualTo(1);
            assertThat(store.get("dict:herd")).contains("coalesced");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void evictDuringSlowLoaderDoesNotRefillStaleValue() throws Exception {
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> slow = pool.submit(() -> cache.get(CacheNamespace.DICT, "stale", String.class, () -> {
                loads.incrementAndGet();
                loaderEntered.countDown();
                awaitLatch(releaseLoader);
                return "stale-old";
            }));
            assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
            cache.evict(CacheNamespace.DICT, "stale");
            releaseLoader.countDown();
            assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo("stale-old");
            assertThat(store.get("dict:stale")).isNull();

            String fresh = cache.get(CacheNamespace.DICT, "stale", String.class, () -> {
                loads.incrementAndGet();
                return "fresh";
            });
            assertThat(fresh).isEqualTo("fresh");
            assertThat(loads.get()).isEqualTo(2);
            assertThat(store.get("dict:stale")).contains("fresh");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void peerBroadcastInvalidateBlocksStaleLoaderFill() throws Exception {
        TwoLevelPlatformCache peer = new TwoLevelPlatformCache(store);
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> slow = pool.submit(() -> peer.get(CacheNamespace.CONFIG, "peer-k", String.class, () -> {
                loads.incrementAndGet();
                loaderEntered.countDown();
                awaitLatch(releaseLoader);
                return "peer-stale";
            }));
            assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
            cache.evict(CacheNamespace.CONFIG, "peer-k");
            releaseLoader.countDown();
            assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo("peer-stale");
            assertThat(store.get("config:peer-k")).isNull();

            String fresh = peer.get(CacheNamespace.CONFIG, "peer-k", String.class, () -> {
                loads.incrementAndGet();
                return "peer-fresh";
            });
            assertThat(fresh).isEqualTo("peer-fresh");
            assertThat(loads.get()).isEqualTo(2);
            assertThat(store.get("config:peer-k")).contains("peer-fresh");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting latch", ex);
        }
    }
}
