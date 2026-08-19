package com.mkt.infra.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    @Test
    void failOpenWhenRedisDown() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.setAvailable(false);
        SlidingWindowRateLimiter limiter =
                new SlidingWindowRateLimiter(store, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        assertThat(limiter.tryAcquire(RateLimitDim.IP, "1.1.1.1", 60, 1)).isTrue();
        assertThat(limiter.tryAcquireLogin("1.1.1.1", "alice", 60, 1, 60, 1)).isTrue();
        assertThat(limiter.degradeEvents()).isGreaterThanOrEqualTo(2);
        assertThat(RateLimitDim.IP.redisKey("1.1.1.1")).isEqualTo("rl:ip:1.1.1.1");
    }
}
