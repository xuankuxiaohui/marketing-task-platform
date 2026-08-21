package com.mkt.risk.testsupport;

import com.mkt.contract.RiskAction;
import com.mkt.risk.application.RiskRuleConfigStore;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MemoryRuleConfigStore implements RiskRuleConfigStore {

    private final CopyOnWriteArrayList<RiskRuleConfigEntity> rows = new CopyOnWriteArrayList<>();

    public MemoryRuleConfigStore() {
        rows.add(row(RiskRuleCode.RA, true, 30, 3600L, RiskAction.REJECT));
        rows.add(row(RiskRuleCode.RB, true, 50, 86400L, RiskAction.REJECT));
        rows.add(row(RiskRuleCode.RC, true, 10, 3600L, RiskAction.REJECT));
        rows.add(row(RiskRuleCode.RD, true, 5, 86400L, RiskAction.REJECT));
        rows.add(row(RiskRuleCode.RE, true, 5, null, RiskAction.REJECT));
        rows.add(row(RiskRuleCode.RF, true, 60, 60L, RiskAction.REJECT));
    }

    public void replace(RuleSpec spec) {
        rows.removeIf(existing -> spec.code().code().equals(existing.getRuleCode()));
        rows.add(fromSpec(spec));
    }

    public void disableAllExcept(RiskRuleCode keep) {
        List<RiskRuleConfigEntity> next = new ArrayList<>();
        for (RiskRuleConfigEntity row : rows) {
            RiskRuleCode code = RiskRuleCode.fromCode(row.getRuleCode());
            row.setEnabled(code == keep ? 1 : 0);
            next.add(row);
        }
        rows.clear();
        rows.addAll(next);
    }

    @Override
    public List<RuleSpec> listAll() {
        List<RuleSpec> specs = new ArrayList<>(rows.size());
        for (RiskRuleConfigEntity row : rows) {
            specs.add(toSpec(row));
        }
        return List.copyOf(specs);
    }

    @Override
    public List<RiskRuleConfigEntity> listRows() {
        return List.copyOf(rows);
    }

    @Override
    public RiskRuleConfigEntity getByCode(String ruleCode) {
        for (RiskRuleConfigEntity row : rows) {
            if (ruleCode.equals(row.getRuleCode())) {
                return row;
            }
        }
        return null;
    }

    @Override
    public int update(RiskRuleConfigEntity entity) {
        for (int i = 0; i < rows.size(); i++) {
            if (entity.getRuleCode().equals(rows.get(i).getRuleCode())) {
                rows.set(i, entity);
                return 1;
            }
        }
        rows.add(entity);
        return 1;
    }

    private static RiskRuleConfigEntity row(
            RiskRuleCode code, boolean enabled, long threshold, Long windowSeconds, RiskAction action) {
        RiskRuleConfigEntity entity = new RiskRuleConfigEntity();
        entity.setId((long) (code.ordinal() + 1));
        entity.setRuleCode(code.code());
        entity.setEnabled(enabled ? 1 : 0);
        entity.setThreshold(threshold);
        entity.setWindowSeconds(windowSeconds);
        entity.setAction(action.name());
        return entity;
    }

    private static RiskRuleConfigEntity fromSpec(RuleSpec spec) {
        return row(spec.code(), spec.enabled(), spec.threshold(), spec.windowSeconds(), spec.action());
    }

    private static RuleSpec toSpec(RiskRuleConfigEntity row) {
        return new RuleSpec(
                RiskRuleCode.fromCode(row.getRuleCode()),
                row.enabledFlag(),
                row.getThreshold(),
                row.getWindowSeconds(),
                RiskAction.valueOf(row.getAction()));
    }
}
