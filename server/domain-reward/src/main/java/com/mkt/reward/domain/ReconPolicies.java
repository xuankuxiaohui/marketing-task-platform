package com.mkt.reward.domain;

/** Category/prize reconActionPolicy (R37.7). */
public final class ReconPolicies {

    public static final String REVIEW = "REVIEW";
    public static final String AUTO = "AUTO";

    private ReconPolicies() {}

    public static boolean valid(String value) {
        return REVIEW.equals(value) || AUTO.equals(value);
    }

    /** Prize null inherits the category policy (design §5.11). */
    public static String effective(String prizePolicy, String categoryPolicy) {
        return prizePolicy == null || prizePolicy.isBlank() ? categoryPolicy : prizePolicy;
    }
}
