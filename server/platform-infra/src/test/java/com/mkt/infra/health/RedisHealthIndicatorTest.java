package com.mkt.infra.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class RedisHealthIndicatorTest {

    @Test
    void upWhenPingSucceeds() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        RedisHealthIndicator indicator = new RedisHealthIndicator(new SessionAvailability(store));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenPingFails() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.setAvailable(false);
        RedisHealthIndicator indicator = new RedisHealthIndicator(new SessionAvailability(store));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).containsEntry("component", "redis");
    }
}
