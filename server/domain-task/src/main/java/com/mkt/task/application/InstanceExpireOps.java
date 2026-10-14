package com.mkt.task.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Instance expire helpers extracted from {@link TaskInstanceAppService} (R12-4).
 * Move-only: expireOne / costSeconds / runInTx semantics unchanged.
 */
@Component
public class InstanceExpireOps {

    private final TaskInstanceStore instances;
    private final InstanceAbandonOps abandonOps;
    private final TransactionTemplate tx;

    @Autowired
    public InstanceExpireOps(
            TaskInstanceStore instances,
            InstanceAbandonOps abandonOps,
            ObjectProvider<TransactionTemplate> tx) {
        this(instances, abandonOps, tx.getIfAvailable());
    }

    public InstanceExpireOps(
            TaskInstanceStore instances, InstanceAbandonOps abandonOps, TransactionTemplate tx) {
        this.instances = instances;
        this.abandonOps = abandonOps;
        this.tx = tx;
    }

    boolean expireOne(long id, Instant now) {
        TaskInstanceEntity row = instances.getByIdForUpdate(id);
        if (row == null || !InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            return false;
        }
        if (row.getExpireAt() == null) {
            return false;
        }
        Instant expireAt = TaskTime.toInstant(row.getExpireAt());
        if (expireAt == null || now.isBefore(expireAt)) {
            return false;
        }
        int cost = costSeconds(row, now);
        int affected = instances.expireCas(id, TaskTime.toUtc(now), cost);
        if (affected != 1) {
            return false;
        }
        abandonOps.append(EventCodes.TASK_INSTANCE_EXPIRE, row, Map.of(
                "instanceId", row.getId(),
                "taskId", row.getTaskId(),
                "userId", row.getUserId(),
                "costSeconds", cost));
        return true;
    }

    private static int costSeconds(TaskInstanceEntity row, Instant now) {
        Instant started = TaskTime.toInstant(row.getStartedAt());
        if (started == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(started, now).toSeconds());
    }

    boolean runInTx(java.util.function.BooleanSupplier action) {
        if (tx != null) {
            Boolean result = tx.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }
}
