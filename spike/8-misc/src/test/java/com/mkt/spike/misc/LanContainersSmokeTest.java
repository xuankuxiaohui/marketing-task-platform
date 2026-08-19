package com.mkt.spike.misc;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanContainersSmokeTest {

    @Test
    void mysqlAndRedisReachableInParallel() {
        String host = env("MYSQL_HOST", "192.168.88.149");
        String port = env("MYSQL_PORT", "3308");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:mysql://" + host + ":" + port + "/" + env("MYSQL_DATABASE", "mkt_platform")
                + "?useSSL=false&allowPublicKeyRetrieval=true");
        ds.setUsername(env("MYSQL_USERNAME", "mkt"));
        ds.setPassword(env("MYSQL_PASSWORD", "Mkt_2026_88x149"));
        Integer one = new JdbcTemplate(ds).queryForObject("SELECT 1", Integer.class);
        assertEquals(1, one);

        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(
                env("REDIS_HOST", "192.168.88.149"),
                Integer.parseInt(env("REDIS_PORT", "6379")));
        redis.setPassword(env("REDIS_PASSWORD", "8whh6RCl8t6ilKHe8KM4"));
        redis.setDatabase(Integer.parseInt(env("REDIS_DATABASE", "2")));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        assertTrue(Boolean.TRUE.equals(template.hasKey("mkt:reserved")));
        factory.destroy();
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
