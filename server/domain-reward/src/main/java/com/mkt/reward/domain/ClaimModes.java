package com.mkt.reward.domain;

public final class ClaimModes {

    public static final String AUTO = "AUTO";
    public static final String MANUAL = "MANUAL";

    private ClaimModes() {}

    public static boolean valid(String value) {
        return AUTO.equals(value) || MANUAL.equals(value);
    }
}
