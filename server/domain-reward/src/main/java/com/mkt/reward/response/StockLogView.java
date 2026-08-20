package com.mkt.reward.response;

import java.time.Instant;

public record StockLogView(
        long id,
        long prizeId,
        String changeType,
        int amount,
        int beforeValue,
        int afterValue,
        String bizSource,
        String bizId,
        Long operatorId,
        Instant createdAt) {}
