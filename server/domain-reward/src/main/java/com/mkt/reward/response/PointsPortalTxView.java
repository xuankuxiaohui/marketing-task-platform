package com.mkt.reward.response;

import java.time.Instant;

public record PointsPortalTxView(
        String type,
        long amount,
        long balanceAfter,
        String bizSource,
        Long sourceTaskId,
        String remark,
        Instant createdAt) {}
