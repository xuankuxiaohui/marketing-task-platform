package com.mkt.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.RedisITSupport;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Same Lua script, 10 calls / max 5; login dual bucket (design §6.3). Requires Docker; leave for CI. */
@Testcontainers
class RateLimitLuaIT {

    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS = RedisITSupport.redis();

    private RedissonClient client;

    @AfterEach
    void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void slidingWindowAllowsFiveOfTenAndLoginNeedsBothBuckets() {
        client = RedisITSupport.client(REDIS);
        SlidingWindowRateLimiter limiter =
                new SlidingWindowRateLimiter(RedisITSupport.store(client), Clock.systemUTC());

        int passed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire(RateLimitDim.IP, "lua-it", 60, 5)) {
                passed++;
            }
        }
        assertThat(passed).isEqualTo(5);

        assertThat(limiter.tryAcquireLogin("10.0.0.1", "alice", 60, 100, 60, 1)).isTrue();
        assertThat(limiter.tryAcquireLogin("10.0.0.2", "alice", 60, 100, 60, 1)).isFalse();
        assertThat(limiter.tryAcquireLogin("10.0.0.1", "bob", 60, 1, 60, 100)).isFalse();
    }
}
