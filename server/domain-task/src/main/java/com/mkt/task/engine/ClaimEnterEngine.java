package com.mkt.task.engine;

import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
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
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * enter after claim INSERT: first min(seq), PASSIVE auto-complete cascade (design §5.1.3 / §5.5).
 * REWARD grant is task 29; a REWARD step is activated and cascade stops.
 */
public final class ClaimEnterEngine {

    private final TaskInstanceStore instances;
    private final EventPublisher events;
    private final Clock clock;
    private final TaskSettings settings;

    public ClaimEnterEngine(
            TaskInstanceStore instances, EventPublisher events, Clock clock, TaskSettings settings) {
        this.instances = instances;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
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
        cascade(instance, snapshot, attrs, crowds, 0);
    }

    private void cascade(
            TaskInstanceEntity instance,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            int fromSeq) {
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
                instances.completeStep(next.getId(), TaskTime.toUtc(now));
                next.setStatus(StepStatuses.COMPLETED);
                appendStepComplete(instance, next);
                seq = next.getSeq() == null ? seq : next.getSeq();
                continue;
            }
            return;
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
                .filter(row -> row.getSeq() != null && row.getSeq() == want && StepStatuses.INACTIVE.equals(row.getStatus()))
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
            events.append(
                    EventCodes.TASK_INSTANCE_COMPLETE,
                    "task_instance",
                    String.valueOf(instance.getId()),
                    Map.of(
                            "instanceId", instance.getId(),
                            "taskId", instance.getTaskId(),
                            "userId", instance.getUserId()));
        }
    }

    private void appendStepComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step) {
        if (events == null) {
            return;
        }
        events.append(
                EventCodes.TASK_STEP_COMPLETE,
                "task_instance",
                String.valueOf(instance.getId()),
                Map.of(
                        "instanceId", instance.getId(),
                        "taskId", instance.getTaskId(),
                        "stepCode", step.getStepCode(),
                        "seq", step.getSeq() == null ? 0 : step.getSeq()));
    }

    private static Integer progressTarget(SnapshotContent snapshot, String stepCode) {
        if (snapshot.steps() == null) {
            return null;
        }
        for (TaskStepCommand step : snapshot.steps()) {
            if (stepCode.equals(step.code())) {
                return step.progressTarget();
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
