package com.marketing.task.security;

import com.marketing.task.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class AdminJwtProvider {
    private final AuthProperties authProperties;

    public AdminJwtProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String issue(String userId) {
        long now = System.currentTimeMillis();
        long expiryMs = authProperties.admin().expiryMinutes() * 60 * 1000L;
        return Jwts.builder()
                .subject(userId)
                .claim("platform", "ADMIN")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(getKey())
                .compact();
    }

    public String verifyAndGetUserId(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
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

    private SecretKey getKey() {
        byte[] keyBytes = authProperties.admin().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
