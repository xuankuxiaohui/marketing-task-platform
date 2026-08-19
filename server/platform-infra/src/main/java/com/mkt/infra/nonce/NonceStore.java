package com.mkt.infra.nonce;

import com.mkt.infra.redis.KeyValueStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SETNX + TTL (design §3.10). Redis down → reject (design §6.8). */
public final class NonceStore {

    private static final Logger log = LoggerFactory.getLogger(NonceStore.class);

    private final KeyValueStore store;

    public NonceStore(KeyValueStore store) {
        this.store = store;
    }

    public static String key(String appId, String nonce) {
        return "nonce:" + appId + ":" + nonce;
    }

    /**
     * @return true if this nonce is new and reserved; false if replayed or Redis is down
     */
    public boolean tryConsume(String appId, String nonce, int ttlSeconds) {
        try {
            return store.setIfAbsent(key(appId, nonce), "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            log.warn("nonce reject on redis failure, appId={}", appId, ex);
            return false;
        }
    }
}
