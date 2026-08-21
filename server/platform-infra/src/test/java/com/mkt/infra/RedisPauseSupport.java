package com.mkt.infra;

import org.testcontainers.containers.GenericContainer;

/** Pause/unpause a Redis Testcontainers instance (design §7.2 RedisPauseSupport). */
public final class RedisPauseSupport {

    private RedisPauseSupport() {}

    public static void pause(GenericContainer<?> redis) {
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
    }

    public static void unpause(GenericContainer<?> redis) {
        redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
    }

    public static void runPaused(GenericContainer<?> redis, Runnable body) {
        pause(redis);
        try {
            body.run();
        } finally {
            unpause(redis);
        }
    }
}
