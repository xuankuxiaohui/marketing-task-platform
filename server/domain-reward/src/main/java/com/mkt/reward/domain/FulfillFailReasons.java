package com.mkt.reward.domain;

import java.util.Set;

/** Closed fulfill_fail_reason values (R18.3). */
public final class FulfillFailReasons {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String ADAPTER_ERROR = "ADAPTER_ERROR";
    public static final String CHANNEL_REJECT = "CHANNEL_REJECT";
    public static final String CALLBACK_FAILED = "CALLBACK_FAILED";
    public static final String MANUAL = "MANUAL";

    public static final Set<String> CLOSED =
            Set.of(TIMEOUT, ADAPTER_ERROR, CHANNEL_REJECT, CALLBACK_FAILED, MANUAL);

    public static final Set<String> AUTO_REFULFILL = Set.of(CHANNEL_REJECT, ADAPTER_ERROR);

    private FulfillFailReasons() {}

    public static boolean closed(String value) {
        return value != null && CLOSED.contains(value);
    }
}
