package com.mkt.task.engine;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.application.TaskProgressReportStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.SkipReasons;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskProgressReportEntity;
import com.mkt.task.expression.CompiledExpression;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.expression.ExpressionCompileException;
import com.mkt.task.expression.ExpressionEngine;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static final int CAS_ATTEMPTS = 3;

    private final TaskInstanceStore instances;
    private final TaskProgressReportStore reports;
    private final EventPublisher events;
    private final Clock clock;
    private final TaskSettings settings;
    private final RewardPort rewards;

    public StepEngine(
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            RewardPort rewards) {
        this.instances = instances;
        this.reports = reports;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        this.rewards = rewards;
    }

    public void enter(
            TaskInstanceEntity instance, SnapshotContent snapshot, UserAttributes attrs, CrowdResolver crowds) {
        List<TaskStepCommand> steps = snapshot.steps() == null ? List.of() : snapshot.steps();
        Instant now = clock.instant();
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
        cascade(instance, snapshot, attrs, crowds, 0, true, new ArrayList<>());
    }

    public StepAdvanceResult click(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds) {
        return completeExternal(Entry.CLICK, instance, step, snapshot, attrs, crowds, null);
    }

    public StepAdvanceResult callback(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String bizNo) {
        if (bizNo != null && !bizNo.isBlank()) {
            instances.updateLastBizNo(step.getId(), bizNo.trim());
            step.setLastBizNo(bizNo.trim());
        }
        return completeExternal(Entry.CALLBACK, instance, step, snapshot, attrs, crowds, null);
    }

    public StepAdvanceResult progress(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int value,
            String reportId) {
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
            TaskInstanceStepEntity fresh = reloadStep(instance.getId(), step.getStepCode());
            return snapshotOf(instance, fresh == null ? step : fresh, snapshot, List.of(), true);
        }
        StepAdvanceResult blocked = precheck(Entry.PROGRESS, instance, step, snapshot);
        if (blocked != null) {
            return blocked;
        }
        if (value < 1 || value > 1000) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return accumulate(instance, step, snapshot, attrs, crowds, value);
    }

    private StepAdvanceResult completeExternal(
            Entry entry,
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            Integer progressCurrent) {
        StepAdvanceResult blocked = precheck(entry, instance, step, snapshot);
        if (blocked != null) {
            return blocked;
        }
        for (int attempt = 0; attempt < CAS_ATTEMPTS; attempt++) {
            TaskInstanceStepEntity current = reloadStep(instance.getId(), step.getStepCode());
            if (current == null) {
                throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
            }
            if (done(current.getStatus())) {
                return snapshotOf(instance, current, snapshot, List.of(), true);
            }
            if (!StepStatuses.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            Instant now = clock.instant();
            int version = current.getVersion() == null ? 0 : current.getVersion();
            int affected = instances.completeStepCas(current.getId(), version, TaskTime.toUtc(now), progressCurrent);
            if (affected == 1) {
                current.setStatus(StepStatuses.COMPLETED);
                current.setCompletedAt(TaskTime.toUtc(now));
                current.setVersion(version + 1);
                if (progressCurrent != null) {
                    current.setProgressCurrent(progressCurrent);
                }
                appendStepComplete(instance, current);
                List<RewardFeedbackView> feedback = new ArrayList<>();
                cascade(instance, snapshot, attrs, crowds, seqOf(current), false, feedback);
                TaskInstanceEntity fresh = instances.getById(instance.getId());
                TaskInstanceStepEntity after = reloadStep(instance.getId(), current.getStepCode());
                return snapshotOf(fresh == null ? instance : fresh, after == null ? current : after, snapshot, feedback, false);
            }
        }
        TaskInstanceStepEntity again = reloadStep(instance.getId(), step.getStepCode());
        if (again != null && done(again.getStatus())) {
            return snapshotOf(instance, again, snapshot, List.of(), true);
        }
        throw new BusinessException(
                entry == Entry.PROGRESS ? TaskErrorCodes.PROGRESS_PROCESSING : TaskErrorCodes.STEP_PROCESSING);
    }

    private StepAdvanceResult accumulate(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int value) {
        Integer target = progressTarget(snapshot, step.getStepCode());
        int goal = target == null ? Integer.MAX_VALUE : target;
        for (int attempt = 0; attempt < CAS_ATTEMPTS; attempt++) {
            TaskInstanceStepEntity current = reloadStep(instance.getId(), step.getStepCode());
            if (current == null) {
                throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
            }
            if (done(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            if (!StepStatuses.ACTIVE.equals(current.getStatus())) {
                throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
            }
            int newCur = (current.getProgressCurrent() == null ? 0 : current.getProgressCurrent()) + value;
            if (newCur >= goal) {
                return completeExternal(Entry.PROGRESS, instance, current, snapshot, attrs, crowds, newCur);
            }
            int version = current.getVersion() == null ? 0 : current.getVersion();
            int affected = instances.addProgressCas(current.getId(), version, newCur);
            if (affected == 1) {
                current.setProgressCurrent(newCur);
                current.setVersion(version + 1);
                return snapshotOf(instance, current, snapshot, List.of(), false);
            }
        }
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
        if (entry == Entry.CLICK && !StepTypes.CLICK.equals(step.getType())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        if (entry == Entry.CALLBACK && !StepTypes.CALLBACK.equals(step.getType())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        if (entry == Entry.PROGRESS && !StepTypes.PROGRESS.equals(step.getType())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        return null;
    }

    private void cascade(
            TaskInstanceEntity instance,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int fromSeq,
            boolean sameRequestInsert,
            List<RewardFeedbackView> feedback) {
        int max = settings.stepMaxCount();
        int seq = fromSeq;
        for (int i = 0; i < max; i++) {
            TaskInstanceStepEntity next = resolveNext(instance.getId(), snapshot, attrs, crowds, seq);
            if (next == null) {
                completeInstance(instance);
                return;
            }
            Instant now = clock.instant();
            instances.activateStep(next.getId(), TaskTime.toUtc(now));
            next.setStatus(StepStatuses.ACTIVE);
            next.setActivatedAt(TaskTime.toUtc(now));
            if (StepTypes.PASSIVE.equals(next.getType())) {
                holdComplete(instance, next, now, null);
                seq = seqOf(next);
                continue;
            }
            if (StepTypes.REWARD.equals(next.getType())) {
                if (!grantReward(instance, next, snapshot, sameRequestInsert, now, feedback)) {
                    return;
                }
                seq = seqOf(next);
                continue;
            }
            return;
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
        if (!StepStatuses.ACTIVE.equals(step.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        holdComplete(instance, step, now, null);
        cascade(instance, snapshot, attrs, crowds, seqOf(step), false, new ArrayList<>());
    }

    private boolean grantReward(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            boolean sameRequestInsert,
            Instant now,
            List<RewardFeedbackView> feedback) {
        if (rewards == null) {
            return false;
        }
        TaskStepCommand def = stepDef(snapshot, step.getStepCode());
        if (def == null || def.prizeId() == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        Long elapsed = null;
        if (!sameRequestInsert) {
            Instant started = TaskTime.toInstant(instance.getStartedAt());
            if (started != null) {
                elapsed = Math.max(0L, Duration.between(started, now).toSeconds());
            }
        }
        boolean simulated = instance.getSimulated() != null && instance.getSimulated() == 1;
        GrantContext ctx = new GrantContext(null, List.of(), null, simulated, elapsed);
        try {
            GrantResult result = rewards.grant(
                    def.prizeId(),
                    instance.getUserId(),
                    GrantSource.TASK_STEP,
                    String.valueOf(step.getId()),
                    ctx);
            if (result != null
                    && (result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON)) {
                holdComplete(instance, step, now, null);
                String name = def.name() == null ? step.getStepCode() : def.name();
                feedback.add(new RewardFeedbackView(name, 1));
                return true;
            }
            return false;
        } catch (RetryableGrantException ex) {
            return false;
        } catch (PermanentGrantException ex) {
            int version = step.getVersion() == null ? 0 : step.getVersion();
            int affected = instances.skipStepCas(
                    step.getId(), version, TaskTime.toUtc(now), SkipReasons.GRANT_PERMANENT_FAILED);
            if (affected == 1) {
                step.setStatus(StepStatuses.SKIPPED);
                step.setSkipReason(SkipReasons.GRANT_PERMANENT_FAILED);
                step.setCompletedAt(TaskTime.toUtc(now));
                step.setVersion(version + 1);
            }
            return true;
        }
    }

    private void holdComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step, Instant now, Integer progress) {
        int version = step.getVersion() == null ? 0 : step.getVersion();
        int affected = instances.completeStepCas(step.getId(), version, TaskTime.toUtc(now), progress);
        if (affected == 0) {
            affected = instances.completeStep(step.getId(), TaskTime.toUtc(now));
        }
        if (affected == 1) {
            step.setStatus(StepStatuses.COMPLETED);
            step.setCompletedAt(TaskTime.toUtc(now));
            step.setVersion(version + 1);
            appendStepComplete(instance, step);
        }
    }

    private TaskInstanceStepEntity resolveNext(
            long instanceId,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int fromSeq) {
        List<TaskInstanceStepEntity> rows = instances.listSteps(instanceId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (fromSeq == 0) {
            return rows.stream()
                    .filter(row -> StepStatuses.INACTIVE.equals(row.getStatus()))
                    .min(Comparator.comparingInt(row -> row.getSeq() == null ? Integer.MAX_VALUE : row.getSeq()))
                    .orElse(null);
        }
        TaskInstanceStepEntity completed = rows.stream()
                .filter(row -> row.getSeq() != null && row.getSeq() == fromSeq)
                .findFirst()
                .orElse(null);
        if (completed != null && snapshot.transitions() != null && !snapshot.transitions().isEmpty()) {
            List<TaskTransitionCommand> edges = snapshot.transitions().stream()
                    .filter(edge -> completed.getStepCode().equals(edge.fromStepCode()))
                    .sorted(Comparator.comparingInt(
                            (TaskTransitionCommand edge) -> edge.priority() == null ? 0 : edge.priority()))
                    .toList();
            Map<String, TaskInstanceStepEntity> byCode = new HashMap<>();
            for (TaskInstanceStepEntity row : rows) {
                byCode.put(row.getStepCode(), row);
            }
            Instant now = clock.instant();
            for (TaskTransitionCommand edge : edges) {
                if (edge.conditionExpr() == null || edge.conditionExpr().isBlank()) {
                    TaskInstanceStepEntity target = byCode.get(edge.toStepCode());
                    if (target != null && StepStatuses.INACTIVE.equals(target.getStatus())) {
                        return target;
                    }
                    continue;
                }
                EvalContext ctx = EvalContexts.branch(
                        attrs,
                        now,
                        crowds,
                        completed.getStepCode(),
                        completed.getProgressCurrent(),
                        progressTarget(snapshot, completed.getStepCode()));
                if (eval(edge.conditionExpr(), ctx)) {
                    TaskInstanceStepEntity target = byCode.get(edge.toStepCode());
                    if (target != null && StepStatuses.INACTIVE.equals(target.getStatus())) {
                        return target;
                    }
                }
            }
        }
        int want = fromSeq + 1;
        return rows.stream()
                .filter(row ->
                        row.getSeq() != null && row.getSeq() == want && StepStatuses.INACTIVE.equals(row.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private void completeInstance(TaskInstanceEntity instance) {
        Instant now = clock.instant();
        Instant started = TaskTime.toInstant(instance.getStartedAt());
        int cost = 0;
        if (started != null) {
            cost = (int) Math.max(0, Duration.between(started, now).toSeconds());
        }
        instances.completeInstance(instance.getId(), TaskTime.toUtc(now), cost);
        instance.setStatus(InstanceStatuses.COMPLETED);
        instance.setCompletedAt(TaskTime.toUtc(now));
        instance.setCostSeconds(cost);
        if (events != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("instanceId", instance.getId());
            payload.put("taskId", instance.getTaskId());
            payload.put("userId", instance.getUserId());
            payload.put("costSeconds", cost);
            payload.put("simulated", simulated(instance));
            events.append(EventCodes.TASK_INSTANCE_COMPLETE, "task_instance", String.valueOf(instance.getId()), payload);
        }
    }

    private void appendStepComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step) {
        if (events == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instance.getId());
        payload.put("taskId", instance.getTaskId());
        payload.put("stepCode", step.getStepCode());
        payload.put("seq", seqOf(step));
        payload.put("userId", instance.getUserId());
        payload.put("simulated", simulated(instance));
        events.append(EventCodes.TASK_STEP_COMPLETE, "task_instance", String.valueOf(instance.getId()), payload);
    }

    private static boolean simulated(TaskInstanceEntity instance) {
        return instance.getSimulated() != null && instance.getSimulated() == 1;
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
        Instant expireAt = TaskTime.toInstant(instance.getExpireAt());
        return expireAt != null && !now.isBefore(expireAt);
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

    private static boolean eval(String expr, EvalContext ctx) {
        try {
            CompiledExpression compiled = ExpressionEngine.compile(expr);
            return ExpressionEngine.evaluate(compiled, ctx);
        } catch (ExpressionCompileException ex) {
            return false;
        }
    }
}
