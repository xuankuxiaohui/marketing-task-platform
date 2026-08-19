package com.mkt.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleDecisionEngineTest {

    @Test
    void registerAndLoginSkipRules() {
        RuleSpec ra = new RuleSpec(RiskRuleCode.RA, true, 1, 3600L, RiskAction.REJECT);
        WindowCounts over = new WindowCounts(10L, null, null, null, null);
        assertThat(RuleDecisionEngine.decide(RiskScene.REGISTER, 1L, over, List.of(ra)).verdict())
                .isEqualTo(RiskAction.PASS);
        assertThat(RuleDecisionEngine.decide(RiskScene.LOGIN, 1L, over, List.of(ra)).hits()).isEmpty();
    }

    @Test
    void claimDoesNotRunRe() {
        RuleSpec re = new RuleSpec(RiskRuleCode.RE, true, 5, null, RiskAction.REJECT);
        RuleOutcome outcome = RuleDecisionEngine.decide(RiskScene.CLAIM, 1L, WindowCounts.empty(), List.of(re));
        assertThat(outcome.verdict()).isEqualTo(RiskAction.PASS);
        assertThat(outcome.hits()).isEmpty();
    }

    @Test
    void grantSkipsReWhenElapsedNull() {
        RuleSpec re = new RuleSpec(RiskRuleCode.RE, true, 5, null, RiskAction.REJECT);
        assertThat(RuleDecisionEngine.decide(RiskScene.GRANT, null, WindowCounts.empty(), List.of(re)).hits())
                .isEmpty();
    }

    @Test
    void grantHitsReBelowThreshold() {
        RuleSpec re = new RuleSpec(RiskRuleCode.RE, true, 5, null, RiskAction.SILENT_REJECT);
        RuleOutcome outcome = RuleDecisionEngine.decide(RiskScene.GRANT, 4L, WindowCounts.empty(), List.of(re));
        assertThat(outcome.verdict()).isEqualTo(RiskAction.SILENT_REJECT);
        assertThat(outcome.hits()).extracting(RuleHit::code).containsExactly(RiskRuleCode.RE);
    }

    @Test
    void grantDoesNotHitReAtThreshold() {
        RuleSpec re = new RuleSpec(RiskRuleCode.RE, true, 5, null, RiskAction.REJECT);
        assertThat(RuleDecisionEngine.decide(RiskScene.GRANT, 5L, WindowCounts.empty(), List.of(re)).hits())
                .isEmpty();
    }

    @Test
    void markDoesNotBlockRejectFromLaterRule() {
        List<RuleSpec> rules = List.of(
                new RuleSpec(RiskRuleCode.RA, true, 1, 3600L, RiskAction.MARK),
                new RuleSpec(RiskRuleCode.RB, true, 1, 3600L, RiskAction.REJECT));
        RuleOutcome outcome =
                RuleDecisionEngine.decide(RiskScene.CLAIM, null, new WindowCounts(2L, 2L, null, null, null), rules);
        assertThat(outcome.verdict()).isEqualTo(RiskAction.REJECT);
        assertThat(outcome.hits()).hasSize(2);
    }

    @Test
    void disabledRuleIsIgnored() {
        RuleSpec ra = new RuleSpec(RiskRuleCode.RA, false, 1, 3600L, RiskAction.REJECT);
        assertThat(RuleDecisionEngine.decide(RiskScene.CLAIM, null, new WindowCounts(9L, null, null, null, null), List.of(ra))
                        .hits())
                .isEmpty();
    }
}
