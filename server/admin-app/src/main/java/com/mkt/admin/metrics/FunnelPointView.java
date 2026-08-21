package com.mkt.admin.metrics;

public record FunnelPointView(
        String period,
        String dimKey,
        long exposureCount,
        long startCount,
        long completeCount,
        Double startRate,
        Double completeRate) {}
