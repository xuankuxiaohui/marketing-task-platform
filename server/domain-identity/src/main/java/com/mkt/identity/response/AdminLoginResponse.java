package com.mkt.identity.response;

import java.util.List;

public record AdminLoginResponse(
        long userId,
        String nickname,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword,
        String csrfToken) {}
