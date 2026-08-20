package com.mkt.identity.response;

import java.time.Instant;
import java.util.List;

public record PortalUserView(
        long id,
        String username,
        String nickname,
        String province,
        String userLevel,
        String userRole,
        List<String> tags,
        String orgId,
        String status,
        Instant registeredAt,
        Instant lastLoginAt,
        Instant createdAt) {

    public PortalUserView {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
