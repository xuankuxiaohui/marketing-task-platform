package com.mkt.reward.response;

import java.time.Instant;

public record PointsTransactionView(
        long id,
        long userId,
        String type,
        long amount,
        long balanceAfter,
        String bizSource,
        String bizId,
        Instant expireAt,
        String remark,
        boolean simulated,
        Instant createdAt) {}
