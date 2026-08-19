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
import com.mkt.infra.outbox.EventConsumer;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.EvtEventLogWriter;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRelay;
import com.mkt.infra.outbox.OutboxStore;
import com.mkt.infra.session.KickReasonStore;
import java.time.Clock;
import javax.sql.DataSource;
import java.util.List;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@EnableScheduling
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

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(JdbcTemplate.class)
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(OutboxStore.class)
    OutboxStore outboxStore(JdbcTemplate jdbcTemplate) {
        return new JdbcOutboxStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(OutboxStore.class)
    @ConditionalOnProperty(name = "mkt.outbox.producer")
    @ConditionalOnMissingBean(EventPublisher.class)
    EventPublisher eventPublisher(OutboxStore outboxStore, @Value("${mkt.outbox.producer}") String producer) {
        return new EventPublisher(outboxStore, OutboxProducer.require(producer));
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(EvtEventLogWriter.class)
    EvtEventLogWriter evtEventLogWriter(JdbcTemplate jdbcTemplate, Clock clock) {
        return new EvtEventLogWriter(jdbcTemplate, clock);
    }

    @Bean
    @ConditionalOnBean({OutboxStore.class, EventPublisher.class})
    @ConditionalOnMissingBean(OutboxRelay.class)
    OutboxRelay outboxRelay(
            OutboxStore outboxStore,
            EventPublisher eventPublisher,
            PlatformLock platformLock,
            Clock clock,
            List<EventConsumer> consumers) {
        return new OutboxRelay(outboxStore, eventPublisher.producer(), platformLock, clock, consumers);
    }

    @Bean
    @ConditionalOnBean(OutboxRelay.class)
    OutboxRelayTicker outboxRelayTicker(OutboxRelay outboxRelay) {
        return new OutboxRelayTicker(outboxRelay);
    }

    public static final class OutboxRelayTicker {

        private final OutboxRelay relay;

        OutboxRelayTicker(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelay = 5000)
        public void tick() {
            relay.tick();
        }
    }
}
