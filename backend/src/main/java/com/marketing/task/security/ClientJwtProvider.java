package com.marketing.task.security;

import com.marketing.task.config.AuthProperties;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.enums.Platform;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientJwtProvider {
    private final AuthProperties authProperties;

    public ClientJwtProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String issue(UserContext userContext) {
        long now = System.currentTimeMillis();
        long expiryMs = authProperties.client().expiryMinutes() * 60 * 1000L;
        return Jwts.builder()
                .subject(userContext.getUserId())
                .claim("province", userContext.getProvince())
                .claim("role", userContext.getRole())
                .claim("tags", userContext.getTags() == null ? "" : String.join(",", userContext.getTags()))
                .claim("orgId", userContext.getOrgId())
                .claim("level", userContext.getLevel())
                .claim("platform", userContext.getPlatform() == null ? null : userContext.getPlatform().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(getKey())
                .compact();
    }

    public UserContext verify(String token) {
        Claims claims = parseToken(token);
        String tagsStr = claims.get("tags", String.class);
        Set<String> tags = tagsStr == null || tagsStr.isBlank()
                ? Set.of()
                : Arrays.stream(tagsStr.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Platform platform = parsePlatform(claims.get("platform", String.class));
        return UserContext.builder()
                .userId(claims.getSubject())
                .province(claims.get("province", String.class))
                .role(claims.get("role", String.class))
                .tags(tags)
                .orgId(claims.get("orgId", String.class))
                .level(claims.get("level", Integer.class))
                .platform(platform)
                .build();
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new AuthenticationException("Token无效或已过期");
        }
    }

    private Platform parsePlatform(String value) {
        if (value == null || value.isBlank()) {
            return Platform.WEB;
        }
        try {
            return Platform.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Platform.WEB;
        }
    }

    private SecretKey getKey() {
        byte[] keyBytes = authProperties.client().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
