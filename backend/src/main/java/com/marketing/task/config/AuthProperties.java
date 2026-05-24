package com.marketing.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean mockEnabled, JwtConfig admin, JwtConfig client) {
    public record JwtConfig(String secret, long expiryMinutes) {
    }
}
