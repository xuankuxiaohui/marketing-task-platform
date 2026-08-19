package com.mkt.infra.degrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.RedisITSupport;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.RedissonKeyValueStore;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis pause: session reject, rate-limit allow, nonce reject (design §6.8). Requires Docker; leave
 * for CI.
 */
@Testcontainers
class DegradeMatrixIT {

    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS = RedisITSupport.redis();

    private RedissonClient client;

    @AfterEach
    void shutdown() {
        try {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        } catch (RuntimeException ignored) {
            // already running
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void pauseRedisAppliesMatrixRows() {
        client = RedisITSupport.client(REDIS);
        RedissonKeyValueStore store = RedisITSupport.store(client);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(store, Clock.systemUTC());
        NonceStore nonces = new NonceStore(store);
        SessionAvailability session = new SessionAvailability(store);

        assertThat(limiter.tryAcquire(RateLimitDim.IP, "ok", 60, 5)).isTrue();
        assertThat(nonces.tryConsume("app", "n-ok", 60)).isTrue();
        session.requireAvailable();

        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();

        assertThat(limiter.tryAcquire(RateLimitDim.IP, "paused", 60, 1)).isTrue();
        assertThat(limiter.degradeEvents()).isGreaterThanOrEqualTo(1);
        assertThat(nonces.tryConsume("app", "n-paused", 60)).isFalse();
        assertThatThrownBy(session::requireAvailable).isInstanceOf(BusinessException.class);
        assertThat(DegradeMatrix.action(DegradeComponent.SESSION)).isEqualTo(DegradeAction.REJECT);
        assertThat(DegradeMatrix.action(DegradeComponent.RATE_LIMIT)).isEqualTo(DegradeAction.ALLOW);
        assertThat(DegradeMatrix.action(DegradeComponent.NONCE)).isEqualTo(DegradeAction.REJECT);
    }
}
