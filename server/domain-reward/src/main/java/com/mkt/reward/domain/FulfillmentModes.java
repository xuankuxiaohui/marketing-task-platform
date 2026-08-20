package com.mkt.reward.domain;

public final class FulfillmentModes {

    public static final String INSTANT = "INSTANT";
    public static final String ASYNC = "ASYNC";

    private FulfillmentModes() {}

    public static boolean valid(String value) {
        return INSTANT.equals(value) || ASYNC.equals(value);
    }
}
