package com.mkt.risk.response;

import java.time.Instant;

public record RiskHitLogResponse(
        long id,
        String hitType,
        String ruleCode,
        Long userId,
        String dimensionValue,
        String context,
        String hitValue,
        String threshold,
        String actionResult,
        boolean simulated,
        Instant occurredAt,
        Instant createdAt) {
}
