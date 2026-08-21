package com.mkt.activity.domain;

/** Closed hit_rule values written on REJECT participation rows (R22.3). */
public final class HitRules {

    public static final String WINDOW = "WINDOW";
    public static final String GRAY = "GRAY";
    public static final String ALLOWLIST = "ALLOWLIST";
    public static final String NEW_USER = "NEW_USER";
    public static final String USER_DAILY = "USER_DAILY";
    public static final String USER_TOTAL = "USER_TOTAL";
    public static final String GLOBAL_DAILY = "GLOBAL_DAILY";
    public static final String REGION = "REGION";

    public static final String PASS = "PASS";
    public static final String REJECT = "REJECT";

    private HitRules() {}
}
