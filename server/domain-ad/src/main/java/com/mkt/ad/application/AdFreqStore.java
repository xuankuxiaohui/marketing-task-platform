package com.mkt.ad.application;

import com.mkt.infra.redis.KeyValueStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Redis freq counters. Failure is fail-closed so C-11 cannot over-serve. */
@Component
public class AdFreqStore {

    private static final Logger log = LoggerFactory.getLogger(AdFreqStore.class);

    private final KeyValueStore store;

    public AdFreqStore(KeyValueStore store) {
        this.store = store;
    }

    public long increment(String key, Duration ttl) {
        try {
            return store.incr(key, ttl);
        } catch (RuntimeException ex) {
            log.warn("ad freq incr failed, key={}", key, ex);
            return Long.MAX_VALUE;
        }
    }

    public long current(String key) {
        try {
            String raw = store.get(key);
            if (raw == null || raw.isBlank()) {
                return 0L;
            }
            return Long.parseLong(raw);
        } catch (RuntimeException ex) {
            log.warn("ad freq get failed, key={}", key, ex);
            return Long.MAX_VALUE;
        }
    }

    public void setTo(String key, long value, Duration ttl) {
        try {
            store.set(key, String.valueOf(value), ttl);
        } catch (RuntimeException ex) {
            log.warn("ad freq set failed, key={}", key, ex);
        }
    }

    public boolean trySetCooldown(String key, Duration ttl) {
        try {
            return store.setIfAbsent(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("ad popup cooldown set failed, key={}", key, ex);
            return false;
        }
    }

    public boolean hasCooldown(String key) {
        try {
            return store.get(key) != null;
        } catch (RuntimeException ex) {
            log.warn("ad popup cooldown get failed, key={}", key, ex);
            return true;
        }
    }
}
