package com.mkt.task.engine;

import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.engine.StepEngine.Entry;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Advance helpers extracted from {@link StepEngine} (R9-3).
 * Move-only: completeExternal / accumulate / precheck semantics unchanged.
 * {@code cascade}, {@code snapshotOf}, {@code rejectIfExpired} (and the helpers they call)
 * are copied so the moved bodies stay verbatim.
 */
@Component
public class StepEngineAdvance {

    static final int CAS_ATTEMPTS = 3;

    private final TaskInstanceStore instances;
    private final Clock clock;
    private final TaskSettings settings;
    private final StepEngineReward rewardOps;
    private final StepEngineCascadeSupport cascadeOps;

    @Autowired
    public StepEngineAdvance(
            TaskInstanceStore instances,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps) {
        this.instances = instances;
        this.clock = clock;
        this.settings = settings;
        this.rewardOps = rewardOps;
        this.cascadeOps = cascadeOps;
    }

    StepAdvanceResult completeExternal(
            Entry entry,
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            Integer progressCurrent,
            String ip,
            String deviceId) {
        StepAdvanceResult blocked = precheck(entry, instance, step, snapshot);
        if (blocked != null) {
            return blocked;
        }
        for (int attempt = 0; attempt < CAS_ATTEMPTS; attempt++) {
            rejectIfExpired(instance, clock.instant());
            TaskInstanceStepEntity current = reloadStep(instance.getId(), step.getStepCode());
            if (current == null) {
                throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
            }
            rejectIfExpired(instance, clock.instant());
            if (done(current.getStatus())) {
                return snapshotOf(instance, current, snapshot, List.of(), true);
            }
            if (!StepStatuses.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            Instant now = clock.instant();
            rejectIfExpired(instance, now);
            int version = current.getVersion() == null ? 0 : current.getVersion();
            int affected = instances.completeStepCas(current.getId(), version, TaskTime.toUtc(now), progressCurrent);
            if (affected == 1) {
                current.setStatus(StepStatuses.COMPLETED);
                current.setCompletedAt(TaskTime.toUtc(now));
                current.setVersion(version + 1);
                if (progressCurrent != null) {
                    current.setProgressCurrent(progressCurrent);
                }
                cascadeOps.appendStepComplete(instance, current);
                List<RewardFeedbackView> feedback = new ArrayList<>();
                cascade(instance, snapshot, attrs, crowds, seqOf(current), false, feedback, ip, deviceId);
                TaskInstanceEntity fresh = instances.getById(instance.getId());
                TaskInstanceStepEntity after = reloadStep(instance.getId(), current.getStepCode());
                return snapshotOf(fresh == null ? instance : fresh, after == null ? current : after, snapshot, feedback, false);
            }
        }
        TaskInstanceStepEntity again = reloadStep(instance.getId(), step.getStepCode());
        rejectIfExpired(instance, clock.instant());
        if (again != null && done(again.getStatus())) {
            return snapshotOf(instance, again, snapshot, List.of(), true);
        }
        throw new BusinessException(
                entry == Entry.PROGRESS ? TaskErrorCodes.PROGRESS_PROCESSING : TaskErrorCodes.STEP_PROCESSING);
    }

    StepAdvanceResult accumulate(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int value,
            String ip,
            String deviceId) {
        Integer target = progressTarget(snapshot, step.getStepCode());
        int goal = target == null ? Integer.MAX_VALUE : target;
        for (int attempt = 0; attempt < CAS_ATTEMPTS; attempt++) {
            rejectIfExpired(instance, clock.instant());
            TaskInstanceStepEntity current = reloadStep(instance.getId(), step.getStepCode());
            if (current == null) {
                throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
            }
            rejectIfExpired(instance, clock.instant());
            if (done(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            if (!StepStatuses.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            int newCur = (current.getProgressCurrent() == null ? 0 : current.getProgressCurrent()) + value;
            if (newCur >= goal) {
                return completeExternal(
                        Entry.PROGRESS, instance, current, snapshot, attrs, crowds, newCur, ip, deviceId);
            }
            int version = current.getVersion() == null ? 0 : current.getVersion();
            rejectIfExpired(instance, clock.instant());
            int affected = instances.addProgressCas(current.getId(), version, newCur);
            if (affected == 1) {
                current.setProgressCurrent(newCur);
                current.setVersion(version + 1);
                return snapshotOf(instance, current, snapshot, List.of(), false);
            }
        }
        rejectIfExpired(instance, clock.instant());
        throw new BusinessException(TaskErrorCodes.PROGRESS_PROCESSING);
    }

    StepAdvanceResult precheck(
            Entry entry, TaskInstanceEntity instance, TaskInstanceStepEntity step, SnapshotContent snapshot) {
        Instant now = clock.instant();
        if (expired(instance, now)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
        if (InstanceStatuses.ABANDONED.equals(instance.getStatus()) && !done(step.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        if (StepStatuses.SKIPPED.equals(step.getStatus()) || StepStatuses.INACTIVE.equals(step.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        requireEntryType(entry, step);
        if (StepStatuses.COMPLETED.equals(step.getStatus())) {
            if (entry == Entry.PROGRESS) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            return snapshotOf(instance, step, snapshot, List.of(), true);
        }
        if (InstanceStatuses.COMPLETED.equals(instance.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(instance.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        if (!StepStatuses.ACTIVE.equals(step.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        return null;
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

    private void cascade(
            TaskInstanceEntity instance,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int fromSeq,
            boolean sameRequestInsert,
            List<RewardFeedbackView> feedback,
            String ip,
            String deviceId) {
        int max = settings.stepMaxCount();
        int seq = fromSeq;
        for (int i = 0; i < max; i++) {
            TaskInstanceStepEntity next = cascadeOps.resolveNext(instance.getId(), snapshot, attrs, crowds, seq);
            if (next == null) {
                cascadeOps.completeInstance(instance);
                return;
            }
            Instant now = clock.instant();
            rejectIfExpired(instance, now);
            if (instances.activateStep(next.getId(), TaskTime.toUtc(now)) != 1) {
                return;
            }
            next.setStatus(StepStatuses.ACTIVE);
            next.setActivatedAt(TaskTime.toUtc(now));
            if (StepTypes.PASSIVE.equals(next.getType())) {
                if (!rewardOps.holdComplete(instance, next)) {
                    return;
                }
                seq = seqOf(next);
                continue;
            }
            if (StepTypes.REWARD.equals(next.getType())) {
                if (!rewardOps.grantReward(instance, next, snapshot, sameRequestInsert, now, feedback, ip, deviceId)) {
                    return;
                }
                seq = seqOf(next);
                continue;
            }
            return;
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

    private static boolean expired(TaskInstanceEntity instance, Instant now) {
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

    private static boolean done(String status) {
        return StepStatuses.COMPLETED.equals(status) || StepStatuses.SKIPPED.equals(status);
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
