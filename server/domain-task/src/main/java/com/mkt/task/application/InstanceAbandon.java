package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.task.domain.AbandonSources;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Instance abandon extracted from {@link TaskInstanceAppService} (R24-3).
 * Move-only: abandonAdmin / abandonUser semantics unchanged.
 */
@Component
public class InstanceAbandon {

    private final TaskInstanceStore instances;
    private final InstanceGuards guards;
    private final InstanceAbandonOps abandonOps;
    private final Clock clock;

    @Autowired
    public InstanceAbandon(
            TaskInstanceStore instances, InstanceGuards guards, InstanceAbandonOps abandonOps, Clock clock) {
        this.instances = instances;
        this.guards = guards;
        this.abandonOps = abandonOps;
        this.clock = clock;
    }

    InstanceAbandonResponse abandonAdmin(long id) {
        TaskInstanceEntity row = instances.getByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            return new InstanceAbandonResponse(row.getStatus());
        }
        return abandonOps.abandon(row, AbandonSources.ADMIN);
    }

    InstanceAbandonResponse abandonUser(long instanceId, long userId, String ip, String deviceId) {
        TaskInstanceEntity row = instances.getByIdForUpdate(instanceId);
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        guards.rejectIfFrozen(userId);
        if (StepEngineExpire.expired(row, clock.instant())) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
        if (InstanceStatuses.ABANDONED.equals(row.getStatus())) {
            return new InstanceAbandonResponse(InstanceStatuses.ABANDONED);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        return abandonOps.abandon(row, AbandonSources.USER);
    }
}
