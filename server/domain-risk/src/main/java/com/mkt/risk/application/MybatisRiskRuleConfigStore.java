package com.mkt.risk.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkt.contract.RiskAction;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import com.mkt.risk.mapper.RiskRuleConfigMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRiskRuleConfigStore implements RiskRuleConfigStore {

    private final RiskRuleConfigMapper mapper;

    public MybatisRiskRuleConfigStore(RiskRuleConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RuleSpec> listAll() {
        List<RiskRuleConfigEntity> rows = listRows();
        List<RuleSpec> specs = new ArrayList<>(rows.size());
        for (RiskRuleConfigEntity row : rows) {
            specs.add(toSpec(row));
        }
        return specs;
    }

    @Override
    public List<RiskRuleConfigEntity> listRows() {
        return mapper.selectList(new LambdaQueryWrapper<RiskRuleConfigEntity>().orderByAsc(RiskRuleConfigEntity::getId));
    }

    @Override
    public RiskRuleConfigEntity getByCode(String ruleCode) {
        return mapper.selectOne(
                new LambdaQueryWrapper<RiskRuleConfigEntity>().eq(RiskRuleConfigEntity::getRuleCode, ruleCode));
    }

    @Override
    public int update(RiskRuleConfigEntity entity) {
        return mapper.updateById(entity);
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
