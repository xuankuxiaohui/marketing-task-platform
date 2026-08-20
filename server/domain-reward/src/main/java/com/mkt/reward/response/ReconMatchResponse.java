package com.mkt.reward.response;

public record ReconMatchResponse(
        int platformCount,
        int channelCount,
        int matchedCount,
        int platformOnly,
        int channelOnly,
        int amountMismatch) {}
