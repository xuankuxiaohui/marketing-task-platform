package com.mkt.contract;

import java.time.Instant;
import java.util.List;

/** Portal profile snapshot (design §2.2.3 / D-12). */
public record UserAttributes(
        String province,
        String userRole,
        String orgId,
        Integer userLevel,
        List<String> tags,
        Instant registeredAt,
        AccountStatus accountStatus) {

    public UserAttributes {
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (accountStatus == null) {
            throw new IllegalArgumentException("accountStatus is required");
        }
    }

    public static UserAttributes notFound() {
        return new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.NOT_FOUND);
    }
}
