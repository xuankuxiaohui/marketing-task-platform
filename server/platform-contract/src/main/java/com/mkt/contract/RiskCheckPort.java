package com.mkt.contract;

/** Cross-domain write port provided by domain-risk (design §2.2.3). */
public interface RiskCheckPort {

    RiskVerdict check(RiskScene scene, RiskSubject subject);

    UserRiskSummary userSummary(long userId);
}
