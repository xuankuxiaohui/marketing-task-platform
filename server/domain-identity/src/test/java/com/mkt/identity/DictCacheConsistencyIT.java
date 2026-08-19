package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.response.DictEntriesCache;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R7.1: dict mutation afterCommit evict is visible on the next read of any instance. */
@Testcontainers
class DictCacheConsistencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void disableTypeClearsEnabledEntriesOnPeerCache() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            var type = env.dictApp.createType(new DictTypeCreateCommand("color_it", "颜色", null));
            env.dictApp.createEntry(new DictEntryCreateCommand("color_it", "红", "red", 1, null));
            assertThat(env.dictApp.listEnabledEntries("color_it")).extracting(item -> item.value()).contains("red");

            TwoLevelPlatformCache peer = new TwoLevelPlatformCache(env.kv);
            DictEntriesCache warm = peer.get(
                    CacheNamespace.DICT, "color_it", DictEntriesCache.class, () -> new DictEntriesCache(null));
            assertThat(warm.items()).isNotEmpty();

            env.dictApp.updateType(type.getId(), new DictTypeUpdateCommand("颜色", "DISABLED", null));
            AtomicInteger loads = new AtomicInteger();
            DictEntriesCache after = peer.get(CacheNamespace.DICT, "color_it", DictEntriesCache.class, () -> {
                loads.incrementAndGet();
                return new DictEntriesCache(java.util.List.of());
            });
            assertThat(after.items()).isEmpty();
            assertThat(env.dictApp.listEnabledEntries("color_it")).isEmpty();
            assertThat(loads.get()).isEqualTo(1);
        }
    }
}
