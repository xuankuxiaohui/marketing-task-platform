package com.mkt.task.engine;

import com.mkt.kernel.BusinessException;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Expiry helpers extracted from {@link StepEngine} (R10-2).
 * Move-only: expired / rejectIfExpired semantics unchanged.
 */
@Component
public class StepEngineExpire {

    @Autowired
    public StepEngineExpire() {
    }

    public static boolean expired(TaskInstanceEntity instance, Instant now) {
        if (InstanceStatuses.EXPIRED.equals(instance.getStatus())) {
            return true;
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(instance.getStatus())) {
            return false;
        }
        Instant expireAt = TaskTime.toInstant(instance.getExpireAt());
        return expireAt != null && !now.isBefore(expireAt);
    }

    static void rejectIfExpired(TaskInstanceEntity instance, Instant now) {
        if (expired(instance, now)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
    }
}
