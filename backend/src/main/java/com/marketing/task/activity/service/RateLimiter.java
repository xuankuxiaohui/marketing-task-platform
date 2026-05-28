package com.marketing.task.activity.service;

import java.time.Duration;

public interface RateLimiter {
    boolean tryAcquire(String key, int maxCount, Duration window);
}
