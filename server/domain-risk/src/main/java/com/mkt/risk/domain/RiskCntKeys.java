package com.mkt.risk.domain;

/** Redis key {@code risk:cnt:{rule}:{dim}:{value}} (design §3.10). */
public final class RiskCntKeys {

    public static final String DIM_USER = "USER";
    public static final String DIM_IP = "IP";
    public static final String DIM_DEVICE = "DEVICE";

    private RiskCntKeys() {
    }

    public static String of(RiskRuleCode rule, String dimension, String value) {
        return "risk:cnt:" + rule.code() + ":" + dimension + ":" + value;
    }
}
