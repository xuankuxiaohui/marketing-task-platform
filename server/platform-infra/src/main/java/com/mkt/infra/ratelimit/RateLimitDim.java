package com.mkt.infra.ratelimit;

/** §6.3 dimensions; Redis key = {@code rl:{dim}:{key}}. */
public enum RateLimitDim {
    IP,
    USER,
    APP_ID,
    CUSTOM;

    public String redisKey(String key) {
        return "rl:" + name().toLowerCase() + ":" + key;
    }
}
