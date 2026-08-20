package com.mkt.identity.application;

import com.mkt.identity.entity.InternalAppEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** In-process 5-minute cache for {@code sys_internal_app} (design §3.2.8). */
public final class InternalAppSecretCache {

    public static final Duration TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public InternalAppEntity get(String appId, Instant now) {
        if (appId == null) {
            return null;
        }
        Entry entry = entries.get(appId);
        if (entry == null || now == null || !now.isBefore(entry.expireAt())) {
            entries.remove(appId);
            return null;
        }
        return entry.app();
    }

    public void put(String appId, InternalAppEntity app, Instant now) {
        if (appId == null || app == null || now == null) {
            return;
        }
        entries.put(appId, new Entry(app, now.plus(TTL)));
    }

    public void evict(String appId) {
        if (appId != null) {
            entries.remove(appId);
        }
    }

    private record Entry(InternalAppEntity app, Instant expireAt) {}
}
