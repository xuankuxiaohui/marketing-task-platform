package com.mkt.reward.domain;

public final class PrizeStatuses {

    public static final String DRAFT = "DRAFT";
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private PrizeStatuses() {}

    public static boolean valid(String value) {
        return DRAFT.equals(value) || ENABLED.equals(value) || DISABLED.equals(value);
    }

    public static boolean everEnabled(String status) {
        return ENABLED.equals(status) || DISABLED.equals(status);
    }
}
