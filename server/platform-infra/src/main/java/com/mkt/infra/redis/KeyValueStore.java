package com.mkt.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Narrow Redis/string facade so unit tests do not need a broker. */
public interface KeyValueStore {

    String get(String key);

    /** Persist without TTL (permanent {@code risk:list} projection, design §3.10). */
    void set(String key, String value);

    void set(String key, String value, Duration ttl);

    void unlink(String key);

    void unlinkByPattern(String pattern);

    void publish(String channel, String payload);

    AutoCloseable subscribe(String channel, Consumer<String> listener);

    boolean setIfAbsent(String key, String value, Duration ttl);

    Long eval(String lua, List<String> keys, List<String> argv);

    boolean tryLock(String key, Duration lease);

    void unlock(String key);

    boolean ping();
}
