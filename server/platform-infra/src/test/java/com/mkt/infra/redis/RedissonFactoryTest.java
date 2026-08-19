package com.mkt.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

class RedissonFactoryTest {

    @Test
    void fourthConstructorArgIsDatabaseIndexNotPoolSize() {
        Config config = RedissonFactory.config(new InfraRedisProperties("10.0.0.8", 6380, "", 2));
        SingleServerConfig server = config.useSingleServer();
        assertThat(server.getDatabase()).isEqualTo(2);
        assertThat(server.getAddress()).isEqualTo("redis://10.0.0.8:6380");
        assertThat(server.getConnectionPoolSize()).isEqualTo(RedissonFactory.CONNECTION_POOL_SIZE);
        assertThat(server.getConnectionMinimumIdleSize()).isEqualTo(RedissonFactory.CONNECTION_MIN_IDLE);
        assertThat(server.getTimeout()).isEqualTo(RedissonFactory.COMMAND_TIMEOUT_MS);
        assertThat(server.getRetryAttempts()).isEqualTo(RedissonFactory.RETRY_ATTEMPTS);
        assertThat(config.getNettyThreads()).isEqualTo(RedissonFactory.NETTY_THREADS);
        assertThat(RedissonFactory.CONNECTION_POOL_SIZE).isGreaterThan(64);
    }
}
