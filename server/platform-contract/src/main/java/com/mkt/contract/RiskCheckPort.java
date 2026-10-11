package com.mkt.contract;

/** Cross-domain write port provided by domain-risk (design §2.2.3). */
public interface RiskCheckPort {

    RiskVerdict check(RiskScene scene, RiskSubject subject);

    UserRiskSummary userSummary(long userId);

    /**
     * Lightweight list membership for hot read paths (portal list blacklist gate).
     * Default falls back to {@link #userSummary}; domain-risk overrides to skip hit-log COUNT.
     */
    default boolean onUserList(long userId, RiskListType type) {
        UserRiskSummary summary = userSummary(userId);
        return summary != null && summary.listStatus().contains(type);
    }
}
