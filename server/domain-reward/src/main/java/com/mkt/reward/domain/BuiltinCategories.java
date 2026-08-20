package com.mkt.reward.domain;

import java.util.Set;

/** Built-in category codes (R17.8 / design §3.4.0). */
public final class BuiltinCategories {

    public static final String POINTS = "POINTS";
    public static final String ALIPAY_RED = "ALIPAY_RED";
    public static final String WECHAT_RED = "WECHAT_RED";
    public static final String PHONE_CREDIT = "PHONE_CREDIT";
    public static final String COUPON = "COUPON";
    public static final String BADGE = "BADGE";
    public static final String PHYSICAL = "PHYSICAL";

    public static final Set<String> ALL = Set.of(
            POINTS, ALIPAY_RED, WECHAT_RED, PHONE_CREDIT, COUPON, BADGE, PHYSICAL);

    /** Must-recon built-in three; seed reconActionPolicy = REVIEW (R17.8 / R37.7). */
    public static final Set<String> RECON_REQUIRED = Set.of(ALIPAY_RED, WECHAT_RED, PHONE_CREDIT);

    private BuiltinCategories() {}

    public static boolean isBuiltin(String code) {
        return code != null && ALL.contains(code);
    }

    public static boolean reconRequired(String code) {
        return code != null && RECON_REQUIRED.contains(code);
    }
}
