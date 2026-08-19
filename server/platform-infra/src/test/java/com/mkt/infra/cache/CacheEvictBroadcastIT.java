package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.RedisITSupport;
import com.mkt.infra.redis.RedissonKeyValueStore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Two instances, afterCommit-equivalent evict broadcasts L1 (design §6.2). Requires Docker; leave for CI. */
@Testcontainers
class CacheEvictBroadcastIT {

    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS = RedisITSupport.redis();

    private RedissonClient clientA;
    private RedissonClient clientB;

    @AfterEach
    void shutdown() {
        if (clientA != null) {
            clientA.shutdown();
        }
        if (clientB != null) {
            clientB.shutdown();
        }
    }

    @Test
    void evictOnAInvalidatesL1OnB() throws InterruptedException {
        clientA = RedisITSupport.client(REDIS);
        clientB = RedisITSupport.client(REDIS);
        TwoLevelPlatformCache cacheA = new TwoLevelPlatformCache(new RedissonKeyValueStore(clientA));
        TwoLevelPlatformCache cacheB = new TwoLevelPlatformCache(new RedissonKeyValueStore(clientB));

        cacheA.put(CacheNamespace.DICT, "province", "GD");
        assertThat(cacheB.get(CacheNamespace.DICT, "province", String.class, () -> "miss")).isEqualTo("GD");

        cacheA.evict(CacheNamespace.DICT, "province");
        Thread.sleep(400);

        AtomicInteger loads = new AtomicInteger();
        String after = cacheB.get(CacheNamespace.DICT, "province", String.class, () -> {
            loads.incrementAndGet();
            return "reloaded";
        });
        assertThat(after).isEqualTo("reloaded");
        assertThat(loads.get()).isEqualTo(1);
    }
}
