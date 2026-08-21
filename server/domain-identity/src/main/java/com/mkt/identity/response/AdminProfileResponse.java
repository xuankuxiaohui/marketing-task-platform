package com.mkt.identity.response;

import java.util.List;

public record AdminProfileResponse(
        long userId,
        String username,
        String nickname,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {

    public AdminProfileResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
