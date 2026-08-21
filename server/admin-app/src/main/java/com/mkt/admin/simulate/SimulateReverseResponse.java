package com.mkt.admin.simulate;

public record SimulateReverseResponse(
        long instanceId, int pointsReversed, int stockRestored, int sendingMarked, boolean channelRevoked) {}
