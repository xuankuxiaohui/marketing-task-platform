package com.mkt.infra.session;

import com.mkt.infra.redis.KeyValueStore;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Write-on-kick, read-once (design §6.1 / D-02). TTL 60s. */
public final class KickReasonStore {

    public static final Duration TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(KickReasonStore.class);

    private final KeyValueStore store;

    public KickReasonStore(KeyValueStore store) {
        this.store = store;
    }

    public static String key(String loginType, String token) {
        return "session:kick-reason:" + loginType + ":" + token;
    }

    public void write(String loginType, String token, KickReason reason) {
        store.set(key(loginType, token), reason.code(), TTL);
    }

    public Optional<KickReason> readAndDelete(String loginType, String token) {
        String redisKey = key(loginType, token);
        String raw = store.get(redisKey);
        if (raw == null) {
            return Optional.empty();
        }
        store.unlink(redisKey);
        return Optional.of(KickReason.fromCode(raw));
    }

    public Optional<KickReason> readAndDeleteQuiet(String loginType, String token) {
        try {
            return readAndDelete(loginType, token);
        } catch (RuntimeException ex) {
            log.warn("kick-reason read failed", ex);
            return Optional.empty();
        }
    }
}
