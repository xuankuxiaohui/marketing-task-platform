package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.entity.InternalAppEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InternalAppSecretCacheTest {

    @Test
    void expiresAfterFiveMinutesAndEvicts() {
        InternalAppSecretCache cache = new InternalAppSecretCache();
        InternalAppEntity app = new InternalAppEntity();
        app.setAppId("app-1");
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        cache.put("app-1", app, now);
        assertThat(cache.get("app-1", now.plusSeconds(299))).isSameAs(app);
        assertThat(cache.get("app-1", now.plus(InternalAppSecretCache.TTL))).isNull();
        cache.put("app-1", app, now);
        cache.evict("app-1");
        assertThat(cache.get("app-1", now)).isNull();
    }
}
