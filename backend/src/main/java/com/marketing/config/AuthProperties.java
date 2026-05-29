package com.marketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean mockEnabled, String adminSecret, String clientSecret) {
}
