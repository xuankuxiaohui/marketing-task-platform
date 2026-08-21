package com.mkt.reward.response;

import java.time.Instant;
import java.time.LocalDate;

public record ReconBatchResponse(
        long id,
        String categoryCode,
        LocalDate billDate,
        String status,
        int platformCount,
        int channelCount,
        int matchedCount,
        int platformOnly,
        int channelOnly,
        int amountMismatch,
        Instant createdAt) {}
