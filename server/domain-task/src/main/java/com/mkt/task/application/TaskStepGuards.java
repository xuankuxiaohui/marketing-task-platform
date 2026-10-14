package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserRiskSummary;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.support.TaskErrorCodes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Guard helpers extracted from {@link TaskStepAppService} (R8-3).
 * Move-only: requireStep / rejectIfFrozen / blacklisted semantics unchanged.
 */
@Component
public class TaskStepGuards {

    private final TaskInstanceStore instances;
    private final RiskCheckPort risk;

    @Autowired
    public TaskStepGuards(TaskInstanceStore instances, ObjectProvider<RiskCheckPort> risk) {
        this(instances, risk.getIfAvailable());
    }

    public TaskStepGuards(TaskInstanceStore instances, RiskCheckPort risk) {
        this.instances = instances;
        this.risk = risk;
    }

    TaskInstanceStepEntity requireStep(long instanceId, String stepCode) {
        if (stepCode == null || stepCode.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskInstanceStepEntity step = instances.getStep(instanceId, stepCode.trim());
        if (step == null) {
            throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
        }
        return step;
    }

    void rejectIfFrozen(long userId, TaskErrorCodes code) {
        if (blacklisted(userId)) {
            throw new BusinessException(code);
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
