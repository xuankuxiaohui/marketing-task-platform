package com.mkt.infra.ratelimit;

import com.mkt.infra.redis.KeyValueStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Same Lua script, keyed by dim (design §6.3). Redis failure = fail-open. */
public final class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private static final String LUA = loadLua();

    private final KeyValueStore store;
    private final Clock clock;
    private final AtomicLong degradeEvents = new AtomicLong();

    public SlidingWindowRateLimiter(KeyValueStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public boolean tryAcquire(RateLimitDim dim, String key, int windowSeconds, int max) {
        long now = clock.millis();
        long windowMs = windowSeconds * 1000L;
        try {
            Long ok = store.eval(
                    LUA,
                    List.of(dim.redisKey(key)),
                    List.of(String.valueOf(now), String.valueOf(windowMs), String.valueOf(max), now + "-" + UUID.randomUUID()));
            return ok != null && ok == 1L;
        } catch (RuntimeException ex) {
            degradeEvents.incrementAndGet();
            log.warn("rate-limit fail-open, dim={}, key={}", dim, key, ex);
            return true;
        }
    }

    /** Login must pass IP and account buckets (design §6.3). */
    public boolean tryAcquireLogin(String ip, String account, int ipWindowSeconds, int ipMax, int accountWindowSeconds, int accountMax) {
        return tryAcquire(RateLimitDim.IP, ip, ipWindowSeconds, ipMax)
                && tryAcquire(RateLimitDim.USER, account, accountWindowSeconds, accountMax);
    }

    public long degradeEvents() {
        return degradeEvents.get();
    }

    private static String loadLua() {
        try (InputStream in = SlidingWindowRateLimiter.class.getResourceAsStream("/infra/rate-limit.lua")) {
            if (in == null) {
                throw new IllegalStateException("missing /infra/rate-limit.lua");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read rate-limit.lua", ex);
        }
    }
}
