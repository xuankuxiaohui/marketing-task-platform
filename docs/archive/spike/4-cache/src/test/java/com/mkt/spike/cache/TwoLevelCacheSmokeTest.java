package com.mkt.spike.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TwoLevelCacheSmokeTest {

    @Autowired
    StringRedisTemplate redis;
    @Autowired
    RedisConnectionFactory factory;

    @Test
    void l2WriteAndReload() {
        String key = "dict:spike:" + UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        String first = load(key, loads);
        String second = redis.opsForValue().get(key);
        assertEquals(first, second);
        assertEquals(1, loads.get());
        redis.delete(key);
    }

    @Test
    void broadcastEvictReachesSecondClient() throws Exception {
        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((MessageListener) (message, pattern) -> {
            payload.set(new String(message.getBody()));
            got.countDown();
        }, new ChannelTopic("cache:evict"));
        container.afterPropertiesSet();
        container.start();
        try {
            String body = "dict:spike:" + UUID.randomUUID();
            redis.convertAndSend("cache:evict", body);
            assertTrue(got.await(3, TimeUnit.SECONDS));
            assertEquals(body, payload.get());
            assertNotEquals(null, payload.get());
        } finally {
            container.stop();
        }
    }

    private String load(String key, AtomicInteger loads) {
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        loads.incrementAndGet();
        String value = "v-" + UUID.randomUUID();
        redis.opsForValue().set(key, value);
        return value;
    }
}
