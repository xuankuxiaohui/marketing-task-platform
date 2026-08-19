package com.mkt.identity.domain;

/** Cache evict granularity (R9.2 / design §4.3). */
public final class CacheEvictLevels {

    public static final String KEY = "KEY";
    public static final String PREFIX = "PREFIX";
    public static final String NAMESPACE = "NAMESPACE";

    private CacheEvictLevels() {}

    public static boolean valid(String level) {
        return KEY.equals(level) || PREFIX.equals(level) || NAMESPACE.equals(level);
    }
}
