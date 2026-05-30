package com.marketing.interceptor;

import java.time.Duration;

public interface RateLimiter {
    boolean tryAcquire(String key, int maxRequests, Duration window);
}