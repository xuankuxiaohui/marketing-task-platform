package com.mkt.reward.response;

import java.time.Instant;

public record PrizeCardView(
        long recordId,
        String prizeName,
        String prizeImage,
        String categoryCode,
        String rewardTarget,
        String fulfillmentMode,
        String status,
        String fulfillmentStatus,
        Instant expireAt,
        Instant obtainedAt,
        Long sourceTaskId,
        String sourceTaskName,
        String failReason,
        String fulfillFailReason) {}
