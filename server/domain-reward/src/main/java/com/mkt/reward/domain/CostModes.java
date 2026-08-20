package com.mkt.reward.domain;

public final class CostModes {

    public static final String NONE = "NONE";
    public static final String FIXED_UNIT = "FIXED_UNIT";
    public static final String FACE_VALUE = "FACE_VALUE";

    private CostModes() {}

    public static boolean valid(String value) {
        return NONE.equals(value) || FIXED_UNIT.equals(value) || FACE_VALUE.equals(value);
    }
}
