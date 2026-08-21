package com.mkt.admin.metrics;

public record RiskPointView(
        String period, String dimKey, long hitCount, long interceptCount, Double interceptRate) {}
