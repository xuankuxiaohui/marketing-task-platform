package com.marketing.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component("localRateLimiter")
public class LocalRateLimiter implements RateLimiter {

    private static final int STRIPE_COUNT = 128;
    private final Object[] locks = new Object[STRIPE_COUNT];
    
    private final Cache<String, Deque<Long>> counters = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LocalRateLimiter() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        int stripe = Math.abs(key.hashCode() % STRIPE_COUNT);
        synchronized (locks[stripe]) {
            Deque<Long> timestamps = counters.get(key, k -> new ConcurrentLinkedDeque<>());
            long now = System.currentTimeMillis();
            long windowStart = now - window.toMillis();

            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}