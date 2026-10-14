package com.mkt.task.application;

import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.ExpireAtCalculator;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.support.TaskSettings;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Claim instance insert extracted from {@link TaskClaimAppService} (R11-2).
 * Move-only: insertInstance / duplicateKey / Inserted semantics unchanged.
 */
@Component
public class ClaimInsert {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskSettings settings;

    @Autowired
    public ClaimInsert(
            TaskVersionSnapshotStore snapshots, TaskInstanceStore instances, TaskSettings settings) {
        this.snapshots = snapshots;
        this.instances = instances;
        this.settings = settings;
    }

    Inserted insertInstance(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            String cycleKey,
            Instant now,
            boolean simulated) {
        Instant cycleEnd = CycleKeyResolver.cycleEnd(
                snapshot.cycleType(),
                snapshot.cronExpr(),
                snapshot.specialStart(),
                snapshot.specialEnd(),
                now);
        Instant expireAt = ExpireAtCalculator.compute(
                snapshot.endTime(),
                TaskTime.toInstant(definition.getOfflineAt()),
                cycleEnd,
                now,
                settings.expireAfterWindowDays());
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(definition.getId(), definition.getVersion());
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(definition.getId());
        row.setTaskCode(definition.getCode());
        row.setVersion(definition.getVersion());
        row.setSnapshotId(snap.getId());
        row.setUserId(userId);
        row.setCycleKey(cycleKey);
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setExpireAt(TaskTime.toUtc(expireAt));
        row.setStartedAt(TaskTime.toUtc(now));
        row.setSimulated(simulated ? 1 : 0);
        row.setCreatedAt(TaskTime.toUtc(now));
        try {
            instances.insert(row);
        } catch (RuntimeException ex) {
            if (!duplicateKey(ex)) {
                throw ex;
            }
            TaskInstanceEntity winner = instances.getByUserTaskCycle(userId, definition.getId(), cycleKey);
            if (winner != null) {
                return new Inserted(winner, false);
            }
            throw ex;
        }
        return new Inserted(row, true);
    }

    private static boolean duplicateKey(Throwable ex) {
        while (ex != null) {
            if (ex instanceof DuplicateKeyException || ex instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    record Inserted(TaskInstanceEntity row, boolean created) {}
}
