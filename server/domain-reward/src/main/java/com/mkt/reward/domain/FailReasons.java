package com.mkt.reward.domain;

import java.util.Set;

/** Closed fail_reason values (design §3.4.2 / R14.5). */
public final class FailReasons {

    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String STOCK_INSUFFICIENT = "STOCK_INSUFFICIENT";
    public static final String PRIZE_DISABLED = "PRIZE_DISABLED";
    public static final String PRIZE_DELETED = "PRIZE_DELETED";
    public static final String USER_INVALID = "USER_INVALID";

    public static final Set<String> RETRYABLE = Set.of(SYSTEM_ERROR, STOCK_INSUFFICIENT);
    public static final Set<String> PERMANENT = Set.of(PRIZE_DISABLED, PRIZE_DELETED, USER_INVALID);

    private FailReasons() {}
}
