package com.mkt.reward.support;

/** Appendix B points permission codes (design §4.5). */
public final class PointsPermissions {

    public static final String ACCOUNT_QUERY = "points:account:query";
    public static final String ACCOUNT_ADJUST = "points:account:adjust";
    public static final String TRANSACTION_QUERY = "points:transaction:query";

    private PointsPermissions() {}
}
