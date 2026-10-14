package com.mkt.task.engine;

import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cascade helpers extracted from {@link StepEngine} (R9-2, R9-4).
 * Move-only: cascade / resolveNext / completeInstance / appendStepComplete semantics unchanged.
 * {@code cascade} stays verbatim, including {@code cascadeOps.*} calls on this collaborator.
 * {@code progressTarget}, {@code rejectIfExpired}, {@code eval} (and the helpers they call)
 * are copied so the moved bodies stay verbatim.
 */
@Component
public class StepEngineCascadeSupport {

    private final TaskInstanceStore instances;
    private final EventPublisher events;
    private final Clock clock;
    private final TaskSettings settings;
    private final StepEngineReward rewardOps;
    private final StepEngineCascadeSupport cascadeOps;

    @Autowired
    public StepEngineCascadeSupport(
            TaskInstanceStore instances,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps) {
        this(instances, events.getIfAvailable(), clock, settings, rewardOps);
    }

    public StepEngineCascadeSupport(
            TaskInstanceStore instances,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps) {
        this.instances = instances;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        this.rewardOps = rewardOps;
        this.cascadeOps = this;
    }

    void cascade(
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

    TaskInstanceStepEntity resolveNext(
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

    void completeInstance(TaskInstanceEntity instance) {
        Instant now = clock.instant();
        rejectIfExpired(instance, now);
        Instant started = TaskTime.toInstant(instance.getStartedAt());
        int cost = 0;
        if (started != null) {
            cost = (int) Math.max(0, Duration.between(started, now).toSeconds());
        }
        if (instances.completeInstance(instance.getId(), TaskTime.toUtc(now), cost) != 1) {
            return;
        }
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

    void appendStepComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step) {
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

    private static void rejectIfExpired(TaskInstanceEntity instance, Instant now) {
        if (expired(instance, now)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
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
