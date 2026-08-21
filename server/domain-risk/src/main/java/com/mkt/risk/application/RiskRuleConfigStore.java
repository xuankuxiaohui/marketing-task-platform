package com.mkt.risk.application;

import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import java.util.List;

public interface RiskRuleConfigStore {

    List<RuleSpec> listAll();

    List<RiskRuleConfigEntity> listRows();

    RiskRuleConfigEntity getByCode(String ruleCode);

    int update(RiskRuleConfigEntity entity);
}
