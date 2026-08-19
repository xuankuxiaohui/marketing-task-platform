package com.mkt.risk.domain;

import com.mkt.contract.RiskAction;

public record RuleHit(RiskRuleCode code, long hitValue, long threshold, RiskAction action) {
}
