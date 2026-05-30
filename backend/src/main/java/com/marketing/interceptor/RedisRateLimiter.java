package com.marketing.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component("redisRateLimiter")
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local max = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local window_start = now - window

            redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
            local count = redis.call('ZCARD', key)
            if count < max then
                redis.call('ZADD', key, now, now .. ':' .. math.random(1, 1000000))
                redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
                return 1
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        try {
            Long result = redisTemplate.execute(
                    SCRIPT,
                    Collections.singletonList("rate_limit:" + key),
                    String.valueOf(maxRequests),
                    String.valueOf(window.toMillis()),
                    String.valueOf(System.currentTimeMillis())
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("Redis rate limiter error, fallback to allow: key={}, error={}", key, e.getMessage());
            return true;
        }
    }
}