package com.mkt.risk.support;

/** {@code risk.fallback-policy} (R26.3). Default allow. */
public enum RiskFallbackPolicy {
    ALLOW,
    REJECT;

    public static RiskFallbackPolicy parse(String raw) {
        if (raw == null || raw.isBlank() || "allow".equalsIgnoreCase(raw.strip())) {
            return ALLOW;
        }
        if ("reject".equalsIgnoreCase(raw.strip())) {
            return REJECT;
        }
        return ALLOW;
    }
}
