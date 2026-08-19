package com.mkt.infra.degrade;

public enum DegradeAction {
    REJECT,
    ALLOW,
    L1_OR_DB,
    SKIP_ROUND,
    FALLBACK_CAS,
    POLICY
}
