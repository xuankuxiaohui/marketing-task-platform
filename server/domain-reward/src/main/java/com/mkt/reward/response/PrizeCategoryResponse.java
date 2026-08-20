package com.mkt.reward.response;

import java.time.Instant;
import java.util.Map;

public record PrizeCategoryResponse(
        String code,
        String name,
        String rewardTarget,
        String fulfillmentMode,
        String costMode,
        boolean reconRequired,
        String reconActionPolicy,
        String adapterCode,
        Map<String, Object> paramSchema,
        boolean builtin,
        String status,
        Instant createdAt) {}
