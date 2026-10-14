package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.TaskStartResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Claim view helpers extracted from {@link TaskClaimAppService} (R11-3).
 * Move-only: toStart / currentStep / snapshotOf / cycleKeyOf / visibility semantics unchanged.
 */
@Component
public class ClaimViews {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;

    @Autowired
    public ClaimViews(
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users) {
        this(snapshots, instances, crowds, users.getIfAvailable());
    }

    public ClaimViews(
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users) {
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
    }

    VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now) {
        StoreCrowdMembership membership = new StoreCrowdMembership(crowds);
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        return VisibilityEvaluator.evaluate(
                definition.getStatus(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.gray(),
                snapshot.filter(),
                definition.getId(),
                userId,
                attrs,
                now,
                membership,
                EvalContexts.filter(attrs, now, resolver));
    }

    SnapshotContent snapshotOf(TaskDefinitionEntity definition) {
        if (definition.getVersion() == null || definition.getVersion() < 1) {
            return null;
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(definition.getId(), definition.getVersion());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    static String cycleKeyOf(SnapshotContent snapshot, TaskDefinitionEntity definition, Instant now) {
        if (snapshot != null) {
            return CycleKeyResolver.resolve(
                    snapshot.cycleType(), snapshot.cronExpr(), snapshot.specialStart(), snapshot.specialEnd(), now);
        }
        return CycleKeyResolver.resolve(
                definition.getCycleType(),
                definition.getCronExpr(),
                TaskTime.toInstant(definition.getSpecialStart()),
                TaskTime.toInstant(definition.getSpecialEnd()),
                now);
    }

    TaskStartResponse toStart(TaskInstanceEntity instance, String platform) {
        SnapshotContent snapshot = snapshotOf(instance);
        CurrentStepView current = currentStep(instance.getId(), snapshot, platform);
        return new TaskStartResponse(instance.getId(), instance.getStatus(), current);
    }

    private SnapshotContent snapshotOf(TaskInstanceEntity instance) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private CurrentStepView currentStep(long instanceId, SnapshotContent snapshot, String platform) {
        List<TaskInstanceStepEntity> steps = instances.listSteps(instanceId);
        TaskInstanceStepEntity current = null;
        if (steps != null) {
            for (TaskInstanceStepEntity step : steps) {
                if (StepStatuses.ACTIVE.equals(step.getStatus())) {
                    current = step;
                    break;
                }
            }
            if (current == null) {
                for (int i = steps.size() - 1; i >= 0; i--) {
                    if (StepStatuses.COMPLETED.equals(steps.get(i).getStatus())) {
                        current = steps.get(i);
                        break;
                    }
                }
            }
        }
        if (current == null) {
            return null;
        }
        TaskStepCommand def = SnapshotViews.step(snapshot, current.getStepCode());
        String name = def == null ? current.getStepCode() : def.name();
        Integer target = def == null ? null : def.progressTarget();
        return new CurrentStepView(
                current.getStepCode(),
                name,
                current.getType(),
                current.getProgressCurrent(),
                target,
                SnapshotViews.action(snapshot, current.getStepCode(), platform));
    }
}
