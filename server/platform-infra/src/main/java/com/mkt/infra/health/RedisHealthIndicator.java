package com.mkt.infra.health;

import com.mkt.infra.degrade.SessionAvailability;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Readiness contributor: Redis down → not ready (design §6.8 session row). */
public final class RedisHealthIndicator implements HealthIndicator {

    private final SessionAvailability availability;

    public RedisHealthIndicator(SessionAvailability availability) {
        this.availability = availability;
    }

    @Override
    public Health health() {
        if (availability.available()) {
            return Health.up().build();
        }
        return Health.down().withDetail("component", "redis").build();
    }
}
