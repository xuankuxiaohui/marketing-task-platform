package com.mkt.reward.domain;

public final class RewardTargets {

    public static final String PLATFORM = "PLATFORM";
    public static final String THIRD_PARTY = "THIRD_PARTY";

    private RewardTargets() {}

    public static boolean valid(String value) {
        return PLATFORM.equals(value) || THIRD_PARTY.equals(value);
    }
}
