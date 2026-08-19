package com.mkt.infra;

import com.mkt.infra.redis.InfraRedisProperties;
import com.mkt.infra.redis.RedissonFactory;
import com.mkt.infra.redis.RedissonKeyValueStore;
import org.redisson.api.RedissonClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public final class RedisITSupport {

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    private RedisITSupport() {
    }

    public static RedissonClient client(GenericContainer<?> redis) {
        return RedissonFactory.create(new InfraRedisProperties(redis.getHost(), redis.getMappedPort(6379), "", 2));
    }

    public static RedissonKeyValueStore store(RedissonClient client) {
        return new RedissonKeyValueStore(client);
    }
}
