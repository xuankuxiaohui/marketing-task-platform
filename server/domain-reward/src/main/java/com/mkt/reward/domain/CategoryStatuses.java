package com.mkt.reward.domain;

public final class CategoryStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private CategoryStatuses() {}

    public static boolean valid(String value) {
        return ENABLED.equals(value) || DISABLED.equals(value);
    }
}
