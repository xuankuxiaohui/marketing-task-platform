package com.mkt.reward.response;

public record SpendRowView(
        String categoryCode, long arrivedCount, long arrivedCostFen, long sendingCount, long sendingCostFen) {}
