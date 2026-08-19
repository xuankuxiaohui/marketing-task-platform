package com.mkt.risk.domain;

import com.mkt.contract.RiskAction;
import java.util.List;

public record RuleOutcome(RiskAction verdict, List<RuleHit> hits) {

    public RuleOutcome {
        if (verdict == null) {
            throw new IllegalArgumentException("verdict is required");
        }
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public static RuleOutcome pass() {
        return new RuleOutcome(RiskAction.PASS, List.of());
    }
}
