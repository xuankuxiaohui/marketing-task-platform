package com.mkt.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-process stand-in for unit tests. Not Embedded Redis. */
public final class MemoryKeyValueStore implements KeyValueStore {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, Long> ttlSecondsByKey = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> zsets = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<String>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Thread> locks = new ConcurrentHashMap<>();
    private volatile boolean available = true;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String get(String key) {
        requireAvailable();
        return values.get(key);
    }

    @Override
    public void set(String key, String value) {
        requireAvailable();
        values.put(key, value);
        ttlSecondsByKey.remove(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        requireAvailable();
        values.put(key, value);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttlSecondsByKey.remove(key);
            return;
        }
        ttlSecondsByKey.put(key, Math.max(1L, ttl.toSeconds()));
    }

    @Override
    public long ttlSeconds(String key) {
        requireAvailable();
        if (!values.containsKey(key)) {
            return -2L;
        }
        Long ttl = ttlSecondsByKey.get(key);
        return ttl == null ? -1L : ttl;
    }

    @Override
    public void expire(String key, Duration ttl) {
        requireAvailable();
        if (!values.containsKey(key)) {
            return;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttlSecondsByKey.remove(key);
            return;
        }
        ttlSecondsByKey.put(key, Math.max(1L, ttl.toSeconds()));
    }

    @Override
    public void unlink(String key) {
        requireAvailable();
        values.remove(key);
        ttlSecondsByKey.remove(key);
        zsets.remove(key);
    }

    @Override
    public void unlinkByPattern(String pattern) {
        requireAvailable();
        String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
        values.keySet().removeIf(k -> k.startsWith(prefix));
        ttlSecondsByKey.keySet().removeIf(k -> k.startsWith(prefix));
        zsets.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public void publish(String channel, String payload) {
        requireAvailable();
        subscribers.getOrDefault(channel, List.of()).forEach(c -> c.accept(payload));
    }

    @Override
    public AutoCloseable subscribe(String channel, Consumer<String> listener) {
        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> subscribers.getOrDefault(channel, List.of()).remove(listener);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        requireAvailable();
        return values.putIfAbsent(key, value) == null;
    }

    @Override
    public Long eval(String lua, List<String> keys, List<String> argv) {
        requireAvailable();
        throw new UnsupportedOperationException("Lua requires Redis");
    }

    @Override
    public boolean tryLock(String key, Duration lease) {
        requireAvailable();
        return locks.putIfAbsent(key, Thread.currentThread()) == null;
    }

    @Override
    public void unlock(String key) {
        requireAvailable();
        locks.remove(key, Thread.currentThread());
    }

    @Override
    public boolean ping() {
        return available;
    }

    @Override
    public void zadd(String key, double score, String member) {
        requireAvailable();
        zsets.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(member, score);
    }

    @Override
    public long zremrangeByScore(String key, double minInclusive, double maxInclusive) {
        requireAvailable();
        Map<String, Double> members = zsets.get(key);
        if (members == null || members.isEmpty()) {
            return 0L;
        }
        long removed = 0L;
        for (var iterator = members.entrySet().iterator(); iterator.hasNext(); ) {
            double score = iterator.next().getValue();
            if (score >= minInclusive && score <= maxInclusive) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public long zcount(String key, double minInclusive, double maxInclusive) {
        requireAvailable();
        Map<String, Double> members = zsets.get(key);
        if (members == null || members.isEmpty()) {
            return 0L;
        }
        long count = 0L;
        for (double score : members.values()) {
            if (score >= minInclusive && score <= maxInclusive) {
                count++;
            }
        }
        return count;
    }

    private void requireAvailable() {
        if (!available) {
            throw new IllegalStateException("redis unavailable");
        }
    }
}
