package com.mkt.infra.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/** Redisson + {@link StringCodec} (spike 1: Lua tonumber). */
public final class RedissonKeyValueStore implements KeyValueStore {

    private final RedissonClient client;

    public RedissonKeyValueStore(RedissonClient client) {
        this.client = client;
    }

    @Override
    public String get(String key) {
        return (String) client.getBucket(key, StringCodec.INSTANCE).get();
    }

    @Override
    public Map<String, String> getMany(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> found = client.getBuckets(StringCodec.INSTANCE).get(keys.toArray(String[]::new));
        if (found == null || found.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : found.entrySet()) {
            if (entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    @Override
    public void set(String key, String value) {
        client.getBucket(key, StringCodec.INSTANCE).set(value);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        client.getBucket(key, StringCodec.INSTANCE).set(value, ttl);
    }

    @Override
    public long ttlSeconds(String key) {
        long millis = client.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
        if (millis == -2L) {
            return -2L;
        }
        if (millis == -1L) {
            return -1L;
        }
        return Math.max(1L, millis / 1000L);
    }

    @Override
    public void expire(String key, Duration ttl) {
        client.getBucket(key, StringCodec.INSTANCE).expire(ttl);
    }

    @Override
    public void unlink(String key) {
        client.getBucket(key, StringCodec.INSTANCE).delete();
    }

    @Override
    public void unlinkByPattern(String pattern) {
        List<String> keys = new ArrayList<>();
        client.getKeys().getKeysByPattern(pattern).forEach(keys::add);
        if (!keys.isEmpty()) {
            client.getKeys().delete(keys.toArray(String[]::new));
        }
    }

    @Override
    public void publish(String channel, String payload) {
        topic(channel).publish(payload);
    }

    @Override
    public AutoCloseable subscribe(String channel, Consumer<String> listener) {
        int id = topic(channel).addListener(String.class, (ch, msg) -> listener.accept(msg));
        return () -> topic(channel).removeListener(id);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return client.getBucket(key, StringCodec.INSTANCE).setIfAbsent(value, ttl);
    }

    @Override
    public Long eval(String lua, List<String> keys, List<String> argv) {
        List<Object> keyArgs = new ArrayList<>(keys);
        return client.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, lua, RScript.ReturnType.LONG, keyArgs, argv.toArray());
    }

    @Override
    public boolean tryLock(String key, Duration lease) {
        RLock lock = client.getLock(key);
        try {
            return lock.tryLock(0, lease.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        RLock lock = client.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public boolean ping() {
        try {
            client.getBucket("__mkt_ping__", StringCodec.INSTANCE).isExists();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void zadd(String key, double score, String member) {
        client.getScoredSortedSet(key, StringCodec.INSTANCE).add(score, member);
    }

    @Override
    public long zremrangeByScore(String key, double minInclusive, double maxInclusive) {
        int removed = client.getScoredSortedSet(key, StringCodec.INSTANCE)
                .removeRangeByScore(minInclusive, true, maxInclusive, true);
        return removed;
    }

    @Override
    public long zcount(String key, double minInclusive, double maxInclusive) {
        int counted = client.getScoredSortedSet(key, StringCodec.INSTANCE)
                .count(minInclusive, true, maxInclusive, true);
        return counted;
    }

    private RTopic topic(String channel) {
        return client.getTopic(channel, StringCodec.INSTANCE);
    }
}
