package com.mkt.identity.response;

import java.util.List;

public record PortalProfileResponse(
        long userId,
        String username,
        String nickname,
        String province,
        String userLevel,
        String userRole,
        List<String> tags,
        long pointsBalance,
        boolean mustChangePassword) {}
