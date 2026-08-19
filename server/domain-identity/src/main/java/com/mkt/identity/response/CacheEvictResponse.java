package com.mkt.identity.response;

public record CacheEvictResponse(int evictedRedis, int notifiedInstances) {}
