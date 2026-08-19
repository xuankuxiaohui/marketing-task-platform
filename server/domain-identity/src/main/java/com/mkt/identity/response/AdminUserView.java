package com.mkt.identity.response;

import java.time.Instant;
import java.util.List;

public record AdminUserView(
        long id,
        String username,
        String nickname,
        String status,
        List<String> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    public AdminUserView {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
