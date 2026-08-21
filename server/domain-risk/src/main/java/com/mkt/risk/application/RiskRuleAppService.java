package com.mkt.risk.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.risk.command.RiskRuleUpdateCommand;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RiskRuleRanges;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import com.mkt.risk.response.RiskRuleResponse;
import com.mkt.risk.support.RiskOperator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskRuleAppService {

    private final RiskRuleConfigStore store;
    private final RiskAuditAppender auditAppender;
    private final Clock clock;

    public RiskRuleAppService(RiskRuleConfigStore store, RiskAuditAppender auditAppender, Clock clock) {
        this.store = store;
        this.auditAppender = auditAppender;
        this.clock = clock;
    }

    public List<RiskRuleResponse> list() {
        List<RiskRuleConfigEntity> rows = store.listRows();
        List<RiskRuleResponse> out = new ArrayList<>(rows.size());
        for (RiskRuleConfigEntity row : rows) {
            out.add(toResponse(row));
        }
        return out;
    }

    @Transactional
    public RiskRuleResponse update(String ruleCode, RiskRuleUpdateCommand command) {
        RiskRuleCode code = parse(ruleCode);
        RiskRuleRanges.assertValid(code, command.threshold(), command.windowSeconds(), command.action());
        RiskRuleConfigEntity existing = store.getByCode(code.code());
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        existing.setEnabled(Boolean.TRUE.equals(command.enabled()) ? 1 : 0);
        existing.setThreshold(command.threshold());
        existing.setWindowSeconds(command.windowSeconds());
        existing.setAction(command.action().name());
        existing.setUpdatedBy(RiskOperator.requireUserId());
        existing.setUpdatedAt(RiskTime.toUtc(clock.instant()));
        store.update(existing);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ruleCode", code.code());
        summary.put("enabled", existing.enabledFlag());
        summary.put("threshold", existing.getThreshold());
        summary.put("windowSeconds", existing.getWindowSeconds());
        summary.put("action", existing.getAction());
        auditAppender.append("rule-config", "risk_rule_config", code.code(), JsonUtil.toJson(summary));
        return toResponse(existing);
    }

    private static RiskRuleCode parse(String ruleCode) {
        try {
            return RiskRuleCode.fromCode(ruleCode);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND, ex);
        }
    }

    private static RiskRuleResponse toResponse(RiskRuleConfigEntity row) {
        return new RiskRuleResponse(
                row.getRuleCode(),
                row.enabledFlag(),
                row.getThreshold() == null ? 0L : row.getThreshold(),
                row.getWindowSeconds(),
                row.getAction(),
                RiskTime.toInstant(row.getUpdatedAt()));
    }
}
