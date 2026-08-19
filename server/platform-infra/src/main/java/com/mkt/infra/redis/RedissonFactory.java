package com.mkt.infra.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

public final class RedissonFactory {

    /** Default is 64, which equals C-12 lost-window concurrency. */
    static final int CONNECTION_POOL_SIZE = 128;

    static final int CONNECTION_MIN_IDLE = 24;

    static final int NETTY_THREADS = 32;

    /** Default 3000ms × 4 retries can exceed the C-12 5s round budget on one stall. */
    static final int COMMAND_TIMEOUT_MS = 1000;

    static final int RETRY_ATTEMPTS = 1;

    private RedissonFactory() {
    }

    public static RedissonClient create(InfraRedisProperties properties) {
        return Redisson.create(config(properties));
    }

    static Config config(InfraRedisProperties properties) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setNettyThreads(NETTY_THREADS);
        SingleServerConfig server = config.useSingleServer()
                .setAddress(properties.address())
                .setDatabase(properties.database())
                .setConnectionPoolSize(CONNECTION_POOL_SIZE)
                .setConnectionMinimumIdleSize(CONNECTION_MIN_IDLE)
                .setTimeout(COMMAND_TIMEOUT_MS)
                .setRetryAttempts(RETRY_ATTEMPTS);
        if (!properties.password().isBlank()) {
            server.setPassword(properties.password());
        }
        return config;
    }
}
