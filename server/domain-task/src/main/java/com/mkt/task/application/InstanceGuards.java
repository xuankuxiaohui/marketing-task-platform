package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserRiskSummary;
import com.mkt.kernel.BusinessException;
import com.mkt.task.support.TaskErrorCodes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Instance guard helpers extracted from {@link TaskInstanceAppService} (R12-2).
 * Move-only: rejectIfFrozen / blacklisted semantics unchanged.
 */
@Component
public class InstanceGuards {

    private final RiskCheckPort risk;

    @Autowired
    public InstanceGuards(ObjectProvider<RiskCheckPort> risk) {
        this(risk.getIfAvailable());
    }

    public InstanceGuards(RiskCheckPort risk) {
        this.risk = risk;
    }

    void rejectIfFrozen(long userId) {
        if (blacklisted(userId)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_FROZEN);
        }
    }

    private boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        UserRiskSummary summary = risk.userSummary(userId);
        return summary != null && summary.listStatus().contains(RiskListType.BLACK);
    }
}
