package com.mkt.risk.application;

import com.mkt.risk.domain.RuleSpec;
import java.util.List;

public interface RiskRuleConfigStore {

    List<RuleSpec> listAll();
}
