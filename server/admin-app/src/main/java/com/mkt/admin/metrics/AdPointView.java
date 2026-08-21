package com.mkt.admin.metrics;

public record AdPointView(
        String period, String dimKey, long exposureCount, long clickCount, Double ctr) {}
