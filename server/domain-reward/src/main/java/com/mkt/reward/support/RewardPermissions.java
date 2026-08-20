package com.mkt.reward.support;

/** Appendix B reward permission codes (design §4.5). */
public final class RewardPermissions {

    public static final String CATEGORY_QUERY = "reward:category:query";
    public static final String CATEGORY_CREATE = "reward:category:create";
    public static final String CATEGORY_UPDATE = "reward:category:update";
    public static final String CATEGORY_DELETE = "reward:category:delete";
    public static final String CATEGORY_DISABLE = "reward:category:disable";
    public static final String CATEGORY_ENABLE = "reward:category:enable";
    public static final String PRIZE_QUERY = "reward:prize:query";
    public static final String PRIZE_CREATE = "reward:prize:create";
    public static final String PRIZE_UPDATE = "reward:prize:update";
    public static final String PRIZE_DELETE = "reward:prize:delete";
    public static final String PRIZE_DISABLE = "reward:prize:disable";
    public static final String PRIZE_ENABLE = "reward:prize:enable";
    public static final String PRIZE_STOCK_REPLENISH = "reward:prize:stock-replenish";
    public static final String RECORD_QUERY = "reward:record:query";
    public static final String RECORD_RETRY = "reward:record:retry";
    public static final String RECORD_MANUAL_GRANT = "reward:record:manual-grant";

    private RewardPermissions() {}
}
