package com.mkt.risk.application;

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
        List<RiskRuleConfigEntity> rows = mapper.selectList(null);
        List<RuleSpec> specs = new ArrayList<>(rows.size());
        for (RiskRuleConfigEntity row : rows) {
            specs.add(new RuleSpec(
                    RiskRuleCode.fromCode(row.getRuleCode()),
                    row.enabledFlag(),
                    row.getThreshold(),
                    row.getWindowSeconds(),
                    RiskAction.valueOf(row.getAction())));
        }
        return specs;
    }
}
