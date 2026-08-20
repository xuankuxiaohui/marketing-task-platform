package com.mkt.reward.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PrizeResponse(
        long id,
        String code,
        String name,
        String imageUrl,
        String description,
        String categoryCode,
        Map<String, Object> typeParams,
        String rewardTarget,
        String fulfillmentMode,
        Integer unitCostFen,
        int totalStock,
        int remainingStock,
        int dailyClaimLimit,
        int totalClaimLimit,
        List<String> regionLimit,
        List<String> levelLimit,
        List<String> tagLimit,
        String claimMode,
        String reconActionPolicy,
        Integer expireHours,
        Long groupId,
        String status,
        Map<String, Object> extConfig,
        Instant createdAt) {}
