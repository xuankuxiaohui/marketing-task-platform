package com.mkt.infra.redis;

/** Defaults to DB 2. Never fall back to db0 (AGENTS / design §2.7.3). */
public record InfraRedisProperties(String host, int port, String password, int database) {

    public InfraRedisProperties {
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        if (port <= 0) {
            port = 6379;
        }
        if (password == null) {
            password = "";
        }
        if (database <= 0) {
            database = 2;
        }
    }

    public static InfraRedisProperties fromEnv() {
        return new InfraRedisProperties(
                env("REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(env("REDIS_PORT", "6379")),
                env("REDIS_PASSWORD", ""),
                Integer.parseInt(env("REDIS_DATABASE", "2")));
    }

    public String address() {
        return "redis://" + host + ":" + port;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
