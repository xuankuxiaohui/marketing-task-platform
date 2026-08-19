package com.mkt.risk.testsupport;

import com.mkt.contract.RiskAction;
import com.mkt.risk.application.RiskRuleConfigStore;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MemoryRuleConfigStore implements RiskRuleConfigStore {

    private final CopyOnWriteArrayList<RuleSpec> rules = new CopyOnWriteArrayList<>();

    public MemoryRuleConfigStore() {
        rules.add(new RuleSpec(RiskRuleCode.RA, true, 30, 3600L, RiskAction.REJECT));
        rules.add(new RuleSpec(RiskRuleCode.RB, true, 50, 86400L, RiskAction.REJECT));
        rules.add(new RuleSpec(RiskRuleCode.RC, true, 10, 3600L, RiskAction.REJECT));
        rules.add(new RuleSpec(RiskRuleCode.RD, true, 5, 86400L, RiskAction.REJECT));
        rules.add(new RuleSpec(RiskRuleCode.RE, true, 5, null, RiskAction.REJECT));
        rules.add(new RuleSpec(RiskRuleCode.RF, true, 60, 60L, RiskAction.REJECT));
    }

    public void replace(RuleSpec spec) {
        rules.removeIf(existing -> existing.code() == spec.code());
        rules.add(spec);
    }

    public void disableAllExcept(RiskRuleCode keep) {
        List<RuleSpec> next = new ArrayList<>();
        for (RuleSpec spec : rules) {
            next.add(new RuleSpec(
                    spec.code(), spec.code() == keep, spec.threshold(), spec.windowSeconds(), spec.action()));
        }
        rules.clear();
        rules.addAll(next);
    }

    @Override
    public List<RuleSpec> listAll() {
        return List.copyOf(rules);
    }
}
