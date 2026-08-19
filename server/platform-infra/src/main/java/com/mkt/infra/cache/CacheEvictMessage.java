package com.mkt.infra.cache;

/** Payload on channel {@code cache:evict} (design §3.10 / §6.2). */
public record CacheEvictMessage(String namespace, String key, EvictMode mode) {

    public enum EvictMode {
        KEY,
        PREFIX,
        NAMESPACE
    }
}
