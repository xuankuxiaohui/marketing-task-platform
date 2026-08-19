package com.mkt.infra.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

public final class RedissonFactory {

    private RedissonFactory() {
    }

    public static RedissonClient create(InfraRedisProperties properties) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        SingleServerConfig server = config.useSingleServer().setAddress(properties.address()).setDatabase(properties.database());
        if (!properties.password().isBlank()) {
            server.setPassword(properties.password());
        }
        return Redisson.create(config);
    }
}
