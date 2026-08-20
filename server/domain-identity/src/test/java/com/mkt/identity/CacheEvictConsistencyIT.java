package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.command.CacheEvictCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R9.1: admin evict rebuilds on the next read of any instance. */
@Testcontainers
class CacheEvictConsistencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void namespaceEvictRebuildsPeerAndAdPositionIsNoop() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            env.platformCache.put(CacheNamespace.DICT, "province", "stale");
            TwoLevelPlatformCache peer = new TwoLevelPlatformCache(env.kv);
            assertThat(peer.get(CacheNamespace.DICT, "province", String.class, () -> "miss")).isEqualTo("stale");

            var result = env.cacheAdmin.evict(new CacheEvictCommand("NAMESPACE", "dict", null, null));
            assertThat(result.notifiedInstances()).isEqualTo(1);

            AtomicInteger loads = new AtomicInteger();
            String after = peer.get(CacheNamespace.DICT, "province", String.class, () -> {
                loads.incrementAndGet();
                return "fresh";
            });
            assertThat(after).isEqualTo("fresh");
            assertThat(loads.get()).isEqualTo(1);

            var ad = env.cacheAdmin.evict(new CacheEvictCommand("KEY", "ad:position", null, "home"));
            assertThat(ad.evictedRedis()).isZero();
        }
    }
}
