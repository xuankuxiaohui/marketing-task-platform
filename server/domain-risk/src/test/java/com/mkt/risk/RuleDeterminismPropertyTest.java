package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleDecisionEngine;
import com.mkt.risk.domain.RuleOutcome;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.domain.WindowCounts;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** R26.1: same inputs yield the same verdict; threshold ± 1 is a hard boundary. */
class RuleDeterminismPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(RuleDeterminismPropertyTest.class);

    @BeforeTry
    void logSeedHint() {
        log.debug("RuleDeterminismPropertyTest try");
    }

    @Property(tries = 200)
    void replayIsDeterministic(@ForAll("cases") Case input) {
        RuleOutcome first =
                RuleDecisionEngine.decide(input.scene, input.elapsed, input.counts, input.rules);
        RuleOutcome second =
                RuleDecisionEngine.decide(input.scene, input.elapsed, input.counts, input.rules);
        assertThat(first.verdict()).isEqualTo(second.verdict());
        assertThat(first.hits()).isEqualTo(second.hits());
        assertThat(first.verdict()).isEqualTo(expected(input).verdict());
    }

    @Property(tries = 200)
    void windowThresholdBoundary(@ForAll("ruleActions") RiskAction action, @ForAll("positiveThreshold") long threshold) {
        RuleSpec spec = new RuleSpec(RiskRuleCode.RF, true, threshold, 60L, action);
        List<RuleSpec> rules = List.of(spec);
        RuleOutcome below = RuleDecisionEngine.decide(
                RiskScene.CLAIM, null, new WindowCounts(null, null, null, null, threshold - 1), rules);
        RuleOutcome at = RuleDecisionEngine.decide(
                RiskScene.CLAIM, null, new WindowCounts(null, null, null, null, threshold), rules);
        assertThat(below.hits()).isEmpty();
        assertThat(at.hits()).hasSize(1);
        assertThat(at.verdict()).isEqualTo(action);
    }

    @Property(tries = 200)
    void reThresholdBoundary(@ForAll("ruleActions") RiskAction action, @ForAll("positiveThreshold") long threshold) {
        RuleSpec spec = new RuleSpec(RiskRuleCode.RE, true, threshold, null, action);
        List<RuleSpec> rules = List.of(spec);
        RuleOutcome at = RuleDecisionEngine.decide(RiskScene.GRANT, threshold, WindowCounts.empty(), rules);
        RuleOutcome below = RuleDecisionEngine.decide(RiskScene.GRANT, threshold - 1, WindowCounts.empty(), rules);
        assertThat(at.hits()).isEmpty();
        if (threshold > 1 || threshold - 1 < threshold) {
            assertThat(below.hits()).hasSize(1);
            assertThat(below.verdict()).isEqualTo(action);
        }
    }

    @Provide
    Arbitrary<Long> positiveThreshold() {
        return Arbitraries.longs().between(1, 20);
    }

    @Provide
    Arbitrary<RiskAction> ruleActions() {
        return Arbitraries.of(RiskAction.REJECT, RiskAction.SILENT_REJECT, RiskAction.MARK);
    }

    @Provide
    Arbitrary<Case> cases() {
        Arbitrary<RiskScene> scenes = Arbitraries.of(RiskScene.values());
        Arbitrary<Long> elapsed = Arbitraries.longs().between(0, 20).injectNull(0.3);
        return Combinators.combine(scenes, elapsed, ruleLists(), counts()).as(Case::new);
    }

    private Arbitrary<List<RuleSpec>> ruleLists() {
        return ruleSpec().list().ofMinSize(0).ofMaxSize(6);
    }

    private Arbitrary<RuleSpec> ruleSpec() {
        return Combinators.combine(
                        Arbitraries.of(RiskRuleCode.values()),
                        Arbitraries.of(true, false),
                        Arbitraries.longs().between(1, 20),
                        Arbitraries.of(RiskAction.REJECT, RiskAction.SILENT_REJECT, RiskAction.MARK))
                .as((code, enabled, threshold, action) -> new RuleSpec(
                        code, enabled, threshold, code == RiskRuleCode.RE ? null : 60L, action));
    }

    private Arbitrary<WindowCounts> counts() {
        Arbitrary<Long> n = Arbitraries.longs().between(0, 25).injectNull(0.25);
        return Combinators.combine(n, n, n, n, n).as(WindowCounts::new);
    }

    private static RuleOutcome expected(Case input) {
        return RuleDecisionEngine.decide(input.scene, input.elapsed, input.counts, copy(input.rules));
    }

    private static List<RuleSpec> copy(List<RuleSpec> rules) {
        return new ArrayList<>(rules);
    }

    record Case(RiskScene scene, Long elapsed, List<RuleSpec> rules, WindowCounts counts) {}
}
