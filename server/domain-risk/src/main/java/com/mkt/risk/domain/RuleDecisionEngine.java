package com.mkt.risk.domain;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure R-a–R-f segment of design §5.9. REGISTER/LOGIN must not reach this engine.
 */
public final class RuleDecisionEngine {

    private static final List<RiskRuleCode> WINDOW_ORDER =
            List.of(RiskRuleCode.RA, RiskRuleCode.RB, RiskRuleCode.RC, RiskRuleCode.RD, RiskRuleCode.RF);

    private RuleDecisionEngine() {
    }

    public static RuleOutcome decide(
            RiskScene scene, Long elapsedSeconds, WindowCounts counts, List<RuleSpec> rules) {
        Objects.requireNonNull(scene, "scene");
        if (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN) {
            return RuleOutcome.pass();
        }
        Map<RiskRuleCode, RuleSpec> byCode = index(rules);
        List<RuleHit> hits = new ArrayList<>();
        for (RiskRuleCode code : WINDOW_ORDER) {
            RuleSpec spec = byCode.get(code);
            if (spec == null || !spec.enabled()) {
                continue;
            }
            Long observed = counts == null ? null : counts.of(code);
            if (observed == null) {
                continue;
            }
            if (observed >= spec.threshold()) {
                hits.add(new RuleHit(code, observed, spec.threshold(), spec.action()));
            }
        }
        RuleSpec re = byCode.get(RiskRuleCode.RE);
        if (re != null && re.enabled() && scene == RiskScene.GRANT && elapsedSeconds != null) {
            if (elapsedSeconds < re.threshold()) {
                hits.add(new RuleHit(RiskRuleCode.RE, elapsedSeconds, re.threshold(), re.action()));
            }
        }
        return new RuleOutcome(merge(hits), hits);
    }

    private static Map<RiskRuleCode, RuleSpec> index(List<RuleSpec> rules) {
        Map<RiskRuleCode, RuleSpec> byCode = new EnumMap<>(RiskRuleCode.class);
        if (rules == null) {
            return byCode;
        }
        for (RuleSpec spec : rules) {
            if (spec != null) {
                byCode.put(spec.code(), spec);
            }
        }
        return byCode;
    }

    private static RiskAction merge(List<RuleHit> hits) {
        RiskAction verdict = RiskAction.PASS;
        for (RuleHit hit : hits) {
            if (hit.action() == RiskAction.REJECT) {
                return RiskAction.REJECT;
            }
            if (hit.action() == RiskAction.SILENT_REJECT) {
                verdict = RiskAction.SILENT_REJECT;
            } else if (hit.action() == RiskAction.MARK && verdict == RiskAction.PASS) {
                verdict = RiskAction.MARK;
            }
        }
        return verdict;
    }
}
