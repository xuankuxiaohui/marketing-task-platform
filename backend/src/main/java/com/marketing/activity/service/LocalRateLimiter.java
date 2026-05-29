package com.marketing.activity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
@ConditionalOnProperty(name = "activity.rate-limiter.type", havingValue = "local", matchIfMissing = true)
public class LocalRateLimiter implements RateLimiter {

    private final Cache<String, Deque<Long>> counters = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    public boolean tryAcquire(String key, int maxCount, Duration window) {
        Deque<Long> timestamps = counters.get(key, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxCount) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }
}
