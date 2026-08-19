package com.mkt.risk.domain;

import com.mkt.contract.RiskListType;

/** Redis key {@code risk:list:{dim}:{type}:{value}} (design §3.10). */
public final class RiskListKeys {

    public static final String PERMANENT = "-1";

    private RiskListKeys() {
    }

    public static String of(RiskDimension dimension, RiskListType listType, String listValue) {
        return "risk:list:" + dimension.name() + ":" + listType.name() + ":" + listValue;
    }
}
