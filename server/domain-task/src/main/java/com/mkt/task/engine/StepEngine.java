package com.mkt.task.engine;

import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.application.TaskProgressReportStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskProgressReportEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;

/**
 * Four-entry step engine (design §5.1 / §5.2). Cascade has no external IO besides RewardPort.
 */
public final class StepEngine {

    public enum Entry {
        ENTER,
        CLICK,
        CALLBACK,
        PROGRESS
    }

    private final TaskInstanceStore instances;
    private final TaskProgressReportStore reports;
    private final Clock clock;
    private final StepEngineReward rewardOps;
    private final StepEngineCascadeSupport cascadeOps;
    private final StepEngineAdvance advanceOps;

    public StepEngine(
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            RewardPort rewards) {
        this(instances, reports, events, clock, settings, new StepEngineReward(instances, events, clock, rewards));
    }

    public StepEngine(
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps) {
        this(
                instances,
                reports,
                clock,
                settings,
                rewardOps,
                new StepEngineCascadeSupport(instances, events, clock, settings, rewardOps));
    }

    public StepEngine(
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps) {
        this(
                instances,
                reports,
                clock,
                settings,
                rewardOps,
                cascadeOps,
                new StepEngineAdvance(instances, clock, cascadeOps));
    }

    public StepEngine(
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps) {
        this.instances = instances;
        this.reports = reports;
        this.clock = clock;
        this.rewardOps = rewardOps;
        this.cascadeOps = cascadeOps;
        this.advanceOps = advanceOps;
    }

    public void enter(
            TaskInstanceEntity instance, SnapshotContent snapshot, UserAttributes attrs, CrowdResolver crowds) {
        enter(instance, snapshot, attrs, crowds, null, null);
    }

    public void enter(
            TaskInstanceEntity instance,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String ip,
            String deviceId) {
        Instant now = clock.instant();
        rejectIfExpired(instance, now);
        if (!InstanceStatuses.IN_PROGRESS.equals(instance.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        List<TaskStepCommand> steps = snapshot.steps() == null ? List.of() : snapshot.steps();
        LocalDateTime utc = TaskTime.toUtc(now);
        for (TaskStepCommand step : steps) {
            TaskInstanceStepEntity row = new TaskInstanceStepEntity();
            row.setInstanceId(instance.getId());
            row.setStepCode(step.code());
            row.setSeq(step.seq());
            row.setType(step.type());
            row.setStatus(StepStatuses.INACTIVE);
            row.setProgressCurrent(0);
            row.setVersion(0);
            row.setCreatedAt(utc);
            instances.insertStep(row);
        }
        cascadeOps.cascade(instance, snapshot, attrs, crowds, 0, true, new ArrayList<>(), ip, deviceId);
    }

    public StepAdvanceResult click(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds) {
        return click(instance, step, snapshot, attrs, crowds, null, null);
    }

    public StepAdvanceResult click(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String ip,
            String deviceId) {
        return advanceOps.completeExternal(Entry.CLICK, instance, step, snapshot, attrs, crowds, null, ip, deviceId);
    }

    public StepAdvanceResult callback(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String bizNo) {
        return callback(instance, step, snapshot, attrs, crowds, bizNo, null, null);
    }

    public StepAdvanceResult callback(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String bizNo,
            String ip,
            String deviceId) {
        rejectIfExpired(instance, clock.instant());
        requireEntryType(Entry.CALLBACK, step);
        if (bizNo != null && !bizNo.isBlank()) {
            instances.updateLastBizNo(step.getId(), bizNo.trim());
            step.setLastBizNo(bizNo.trim());
        }
        return advanceOps.completeExternal(Entry.CALLBACK, instance, step, snapshot, attrs, crowds, null, ip, deviceId);
    }

    public StepAdvanceResult progress(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int value,
            String reportId) {
        rejectIfExpired(instance, clock.instant());
        requireEntryType(Entry.PROGRESS, step);
        if (reports == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        TaskProgressReportEntity report = new TaskProgressReportEntity();
        report.setInstanceId(instance.getId());
        report.setStepCode(step.getStepCode());
        report.setReportId(reportId);
        report.setValue(value);
        report.setCreatedAt(TaskTime.toUtc(clock.instant()));
        try {
            reports.insert(report);
        } catch (DuplicateKeyException ex) {
            rejectIfExpired(instance, clock.instant());
            TaskInstanceStepEntity fresh = reloadStep(instance.getId(), step.getStepCode());
            return snapshotOf(instance, fresh == null ? step : fresh, snapshot, List.of(), true);
        }
        StepAdvanceResult blocked = advanceOps.precheck(Entry.PROGRESS, instance, step, snapshot);
        if (blocked != null) {
            return blocked;
        }
        if (value < 1 || value > 1000) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return advanceOps.accumulate(instance, step, snapshot, attrs, crowds, value, null, null);
    }

    private static void requireEntryType(Entry entry, TaskInstanceStepEntity step) {
        String expected = switch (entry) {
            case CLICK -> StepTypes.CLICK;
            case CALLBACK -> StepTypes.CALLBACK;
            case PROGRESS -> StepTypes.PROGRESS;
            case ENTER -> null;
        };
        if (expected != null && !expected.equals(step.getType())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
    }

    /**
     * Retry/scheduler path: CAS-complete an ACTIVE REWARD step and continue cascade.
     * Terminal instances keep the grant but are not revived (R14.10).
     */
    public void resumeFromReward(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds) {
        if (instance == null || step == null) {
            return;
        }
        if (InstanceStatuses.terminal(instance.getStatus())) {
            return;
        }
        if (!StepStatuses.ACTIVE.equals(step.getStatus()) || !StepTypes.REWARD.equals(step.getType())) {
            return;
        }
        Instant now = clock.instant();
        if (expired(instance, now)) {
            return;
        }
        try {
            if (!rewardOps.holdComplete(instance, step)) {
                return;
            }
            cascadeOps.cascade(instance, snapshot, attrs, crowds, seqOf(step), false, new ArrayList<>(), null, null);
        } catch (BusinessException ex) {
            if (ex.errorCode() != TaskErrorCodes.INSTANCE_EXPIRED) {
                throw ex;
            }
            // A retry has already granted the reward. Stop the now-expired cascade
            // without rolling back that entitlement.
        }
    }

    private StepAdvanceResult snapshotOf(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            List<RewardFeedbackView> feedback,
            boolean idempotent) {
        TaskInstanceEntity fresh = instances.getById(instance.getId());
        TaskInstanceEntity row = fresh == null ? instance : fresh;
        return new StepAdvanceResult(row, step, progressTarget(snapshot, step.getStepCode()), feedback, idempotent);
    }

    private TaskInstanceStepEntity reloadStep(long instanceId, String stepCode) {
        return instances.getStep(instanceId, stepCode);
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

    private static void rejectIfExpired(TaskInstanceEntity instance, Instant now) {
        if (expired(instance, now)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
    }

    private static int seqOf(TaskInstanceStepEntity step) {
        return step.getSeq() == null ? 0 : step.getSeq();
    }

    private static Integer progressTarget(SnapshotContent snapshot, String stepCode) {
        TaskStepCommand def = stepDef(snapshot, stepCode);
        return def == null ? null : def.progressTarget();
    }

    private static TaskStepCommand stepDef(SnapshotContent snapshot, String stepCode) {
        if (snapshot == null || snapshot.steps() == null || stepCode == null) {
            return null;
        }
        for (TaskStepCommand step : snapshot.steps()) {
            if (stepCode.equals(step.code())) {
                return step;
            }
        }
        return null;
    }
}
