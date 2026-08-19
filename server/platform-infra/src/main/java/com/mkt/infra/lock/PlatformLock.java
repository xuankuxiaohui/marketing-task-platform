package com.mkt.infra.lock;

import com.mkt.infra.redis.KeyValueStore;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Redisson tryLock(0); claim lock watchdog 30s (design §6.8). */
public final class PlatformLock {

    public static final Duration CLAIM_WATCHDOG = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(PlatformLock.class);

    private final KeyValueStore store;

    public PlatformLock(KeyValueStore store) {
        this.store = store;
    }

    public LockAcquire tryLock(String key) {
        return tryLock(key, Duration.ofSeconds(30));
    }

    public LockAcquire tryLock(String key, Duration lease) {
        try {
            return store.tryLock(key, lease) ? LockAcquire.ACQUIRED : LockAcquire.BUSY;
        } catch (RuntimeException ex) {
            log.warn("lock unavailable, key={}", key, ex);
            return LockAcquire.DEGRADED;
        }
    }

    public LockAcquire tryClaimLock(long recordId) {
        return tryLock(LockKeys.rwdClaim(recordId), CLAIM_WATCHDOG);
    }

    public void unlock(String key) {
        try {
            store.unlock(key);
        } catch (RuntimeException ex) {
            log.warn("unlock failed, key={}", key, ex);
        }
    }
}
