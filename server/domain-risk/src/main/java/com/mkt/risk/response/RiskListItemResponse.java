package com.mkt.risk.response;

import java.time.Instant;

public record RiskListItemResponse(
        long id,
        String dimension,
        String listType,
        String listValue,
        String reason,
        boolean denyLogin,
        Instant effectiveAt,
        Instant expireAt,
        long operatorId,
        String remark,
        Instant createdAt) {
}
