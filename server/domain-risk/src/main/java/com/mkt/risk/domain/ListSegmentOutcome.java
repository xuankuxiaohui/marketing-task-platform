package com.mkt.risk.domain;

/** List-segment result plus the hit descriptor used for {@code risk_hit_log.rule_code}. */
public record ListSegmentOutcome(ListDecision decision, String ruleCode, String dimensionValue) {

    public static ListSegmentOutcome of(ListDecision decision) {
        return new ListSegmentOutcome(decision, null, null);
    }

    public static ListSegmentOutcome reject(String ruleCode, String dimensionValue) {
        return new ListSegmentOutcome(ListDecision.REJECT, ruleCode, dimensionValue);
    }
}
