package com.mkt.infra;

import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.InfraRedisProperties;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.infra.redis.RedissonFactory;
import com.mkt.infra.redis.RedissonKeyValueStore;
import com.mkt.infra.session.KickReasonStore;
import java.time.Clock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class InfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    RedissonClient redissonClient() {
        return RedissonFactory.create(InfraRedisProperties.fromEnv());
    }

    @Bean
    @ConditionalOnMissingBean(KeyValueStore.class)
    KeyValueStore keyValueStore(RedissonClient redissonClient) {
        return new RedissonKeyValueStore(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformCache.class)
    PlatformCache platformCache(KeyValueStore keyValueStore) {
        return new TwoLevelPlatformCache(keyValueStore);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformLock.class)
    PlatformLock platformLock(KeyValueStore keyValueStore) {
        return new PlatformLock(keyValueStore);
    }

    @Bean
    @ConditionalOnMissingBean(SlidingWindowRateLimiter.class)
    SlidingWindowRateLimiter slidingWindowRateLimiter(KeyValueStore keyValueStore, Clock clock) {
        return new SlidingWindowRateLimiter(keyValueStore, clock);
    }

    @Bean
    @ConditionalOnMissingBean(KickReasonStore.class)
    KickReasonStore kickReasonStore(KeyValueStore keyValueStore) {
        return new KickReasonStore(keyValueStore);
    }

    @Bean
    @ConditionalOnMissingBean(NonceStore.class)
    NonceStore nonceStore(KeyValueStore keyValueStore) {
        return new NonceStore(keyValueStore);
    }

    @Bean
    @ConditionalOnMissingBean(SessionAvailability.class)
    SessionAvailability sessionAvailability(KeyValueStore keyValueStore) {
        return new SessionAvailability(keyValueStore);
    }
}
