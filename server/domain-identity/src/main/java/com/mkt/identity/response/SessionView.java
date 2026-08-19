package com.mkt.identity.response;

import java.time.Instant;

public record SessionView(
        String tokenLast4,
        String account,
        String accountType,
        Instant loginAt,
        Instant lastActiveAt,
        String ip,
        String deviceId) {}
