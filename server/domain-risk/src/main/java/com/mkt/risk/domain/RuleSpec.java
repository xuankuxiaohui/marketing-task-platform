package com.mkt.risk.domain;

import com.mkt.contract.RiskAction;

/** Snapshot of {@code risk_rule_config} used by the pure rule engine. */
public record RuleSpec(
        RiskRuleCode code, boolean enabled, long threshold, Long windowSeconds, RiskAction action) {

    public RuleSpec {
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be > 0");
        }
        if (action == null || action == RiskAction.PASS) {
            throw new IllegalArgumentException("action must be REJECT, SILENT_REJECT, or MARK");
        }
    }
}
