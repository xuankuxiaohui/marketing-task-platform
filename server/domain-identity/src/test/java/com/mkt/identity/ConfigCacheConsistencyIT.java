package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.command.ConfigCreateCommand;
import com.mkt.identity.command.ConfigUpdateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.kernel.json.JsonUtil;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R8.1: config mutation afterCommit evict is visible on the next read of any instance. */
@Testcontainers
class ConfigCacheConsistencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void updateValueReloadsPeerCache() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            env.configApp.create(new ConfigCreateCommand("it.cache.n", "it", "1", "NUMBER", false, null));
            env.platformCache.put(CacheNamespace.CONFIG, "it.cache.n", "1");
            TwoLevelPlatformCache peer = new TwoLevelPlatformCache(env.kv);
            assertThat(peer.get(CacheNamespace.CONFIG, "it.cache.n", String.class, () -> "miss")).isEqualTo("1");

            env.configApp.update(
                    "it.cache.n", JsonUtil.fromJson("{\"value\":\"9\"}", ConfigUpdateCommand.class));
            AtomicInteger loads = new AtomicInteger();
            String after = peer.get(CacheNamespace.CONFIG, "it.cache.n", String.class, () -> {
                loads.incrementAndGet();
                return "9";
            });
            assertThat(after).isEqualTo("9");
            assertThat(loads.get()).isEqualTo(1);
        }
    }
}
