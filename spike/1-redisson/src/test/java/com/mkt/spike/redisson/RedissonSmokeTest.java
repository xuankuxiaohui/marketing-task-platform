package com.mkt.spike.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedissonSmokeTest {

    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count < limit then
              redis.call('ZADD', key, now, member)
              redis.call('PEXPIRE', key, window)
              return 1
            end
            return 0
            """;

    private RedissonClient client;

    @BeforeEach
    void start() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + env("REDIS_HOST", "192.168.88.149") + ":" + env("REDIS_PORT", "6379"))
                .setPassword(env("REDIS_PASSWORD", "8whh6RCl8t6ilKHe8KM4"))
                .setDatabase(Integer.parseInt(env("REDIS_DATABASE", "2")));
        client = Redisson.create(config);
    }

    @AfterEach
    void stop() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void rlockExactlyOneWinner() throws Exception {
        String name = "lock:sched:test:" + UUID.randomUUID();
        RLock lock = client.getLock(name);
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Runnable r = () -> {
            try {
                start.await();
                if (lock.tryLock(0, 10, TimeUnit.SECONDS)) {
                    winners.incrementAndGet();
                    Thread.sleep(200);
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, winners.get());
    }

    @Test
    void watchdogRenewsBeyondThirtySeconds() throws Exception {
        RLock lock = client.getLock("lock:sched:watchdog");
        assertTrue(lock.tryLock(1, -1, TimeUnit.SECONDS));
        try {
            Thread.sleep(32_000);
            assertTrue(lock.isHeldByCurrentThread());
        } finally {
            lock.unlock();
        }
    }

    @Test
    void luaSlidingWindowAllowsFiveOfTen() {
        String key = "rl:spike:" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        int passed = 0;
        for (int i = 0; i < 10; i++) {
            Long ok = client.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE,
                    LUA,
                    RScript.ReturnType.LONG,
                    List.of(key),
                    String.valueOf(now),
                    "1000",
                    "5",
                    now + "-" + i
            );
            if (ok != null && ok == 1L) {
                passed++;
            }
        }
        client.getKeys().delete(key);
        assertEquals(5, passed);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
