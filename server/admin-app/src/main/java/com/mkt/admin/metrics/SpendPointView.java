package com.mkt.admin.metrics;

public record SpendPointView(
        String period,
        String dimKey,
        long arrivedCount,
        long arrivedCostFen,
        long sendingCount,
        long sendingCostFen,
        long remainingStock,
        long totalStock) {}
