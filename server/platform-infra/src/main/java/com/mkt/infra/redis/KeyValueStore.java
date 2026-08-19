package com.mkt.infra.redis;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Narrow Redis/string facade so unit tests do not need a broker. */
public interface KeyValueStore {

    String get(String key);

    /** One round-trip when the backend supports MGET; default loops {@link #get}. */
    default Map<String, String> getMany(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys) {
            String value = get(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    /** Persist without TTL (permanent {@code risk:list} projection, design §3.10). */
    void set(String key, String value);

    void set(String key, String value, Duration ttl);

    /**
     * Remaining TTL in seconds. {@code -2} missing, {@code -1} no expire (Sa-Token convention).
     */
    long ttlSeconds(String key);

    void expire(String key, Duration ttl);

    void unlink(String key);

    void unlinkByPattern(String pattern);

    void publish(String channel, String payload);

    AutoCloseable subscribe(String channel, Consumer<String> listener);

    boolean setIfAbsent(String key, String value, Duration ttl);

    Long eval(String lua, List<String> keys, List<String> argv);

    boolean tryLock(String key, Duration lease);

    void unlock(String key);

    boolean ping();

    /** ZADD for {@code risk:cnt} sliding windows (design §3.10 / §5.9). */
    void zadd(String key, double score, String member);

    long zremrangeByScore(String key, double minInclusive, double maxInclusive);

    long zcount(String key, double minInclusive, double maxInclusive);
}
