package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.domain.CrowdMembership;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.InstanceStepView;
import com.mkt.task.response.TaskBriefView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Detail helpers extracted from {@link TaskPortalAppService} (R1-7).
 * Move-only: anonymousDetail / inProgressDetail / visibility / pickInProgress /
 * snapshotOf / snapshotJson / loadSnapshotJson / blacklisted semantics unchanged.
 */
@Component
public class TaskPortalDetail {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final PlatformCache cache;

    @Autowired
    public TaskPortalDetail(
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            ObjectProvider<PlatformCache> cache) {
        this(
                snapshots,
                instances,
                crowds,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                cache.getIfAvailable());
    }

    public TaskPortalDetail(
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            PlatformCache cache) {
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.risk = risk;
        this.cache = cache;
    }

    TaskDetailResponse anonymousDetail(TaskDefinitionEntity definition, Instant now) {
        SnapshotContent snapshot = snapshotOf(definition);
        if (snapshot == null
                || !VisibilityEvaluator.publicCard(
                        definition.getStatus(),
                        snapshot.startTime(),
                        snapshot.endTime(),
                        snapshot.gray(),
                        snapshot.filter(),
                        now)) {
            return new TaskDetailResponse(InstanceStatuses.OFFLINE, null, null, null, null, null, null);
        }
        return new TaskDetailResponse(
                InstanceStatuses.NOT_STARTED,
                null,
                new TaskBriefView(snapshot.name(), snapshot.iconUrl(), snapshot.description(), snapshot.category()),
                SnapshotViews.stepsPreview(snapshot),
                SnapshotViews.rewardPreview(snapshot),
                null,
                null);
    }

    TaskDetailResponse inProgressDetail(TaskInstanceEntity instance, String platform) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        SnapshotContent snapshot =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        List<TaskInstanceStepEntity> steps = instances.listSteps(instance.getId());
        List<InstanceStepView> stepViews = new ArrayList<>();
        CurrentStepView current = null;
        if (steps != null && snapshot != null) {
            for (TaskInstanceStepEntity step : steps) {
                TaskStepCommand def = SnapshotViews.step(snapshot, step.getStepCode());
                String name = def == null ? step.getStepCode() : def.name();
                Integer target = def == null ? null : def.progressTarget();
                stepViews.add(new InstanceStepView(
                        step.getStepCode(),
                        name,
                        step.getType(),
                        step.getStatus(),
                        step.getProgressCurrent() == null ? 0 : step.getProgressCurrent(),
                        target));
                if (current == null && StepStatuses.ACTIVE.equals(step.getStatus())) {
                    current = new CurrentStepView(
                            step.getStepCode(),
                            name,
                            step.getType(),
                            step.getProgressCurrent(),
                            target,
                            SnapshotViews.action(snapshot, step.getStepCode(), platform));
                }
            }
        }
        return new TaskDetailResponse(
                InstanceStatuses.IN_PROGRESS,
                instance.getId(),
                null,
                null,
                SnapshotViews.rewardPreview(snapshot),
                stepViews,
                current);
    }

    VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now) {
        StoreCrowdMembership membership = new StoreCrowdMembership(crowds);
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        return visibility(
                definition, snapshot, userId, attrs, now, membership, EvalContexts.filter(attrs, now, resolver));
    }

    VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now,
            CrowdMembership membership,
            EvalContext filterCtx) {
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
                filterCtx);
    }

    SnapshotContent snapshotOf(TaskDefinitionEntity definition) {
        if (definition.getVersion() == null || definition.getVersion() < 1) {
            return null;
        }
        String json = snapshotJson(definition.getId(), definition.getVersion());
        return json == null ? null : JsonUtil.fromJson(json, SnapshotContent.class);
    }

    String snapshotJson(long taskId, int version) {
        if (cache != null) {
            return cache.get(
                    CacheNamespace.TASK_SNAPSHOT,
                    taskId + ":" + version,
                    String.class,
                    () -> loadSnapshotJson(taskId, version));
        }
        return loadSnapshotJson(taskId, version);
    }

    String loadSnapshotJson(long taskId, int version) {
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, version);
        return snap == null ? null : snap.getContent();
    }

    boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        return risk.onUserList(userId, RiskListType.BLACK);
    }

    TaskInstanceEntity pickInProgress(long taskId, long userId, String cycleKey) {
        TaskInstanceEntity fallback = null;
        for (TaskInstanceEntity row : instances.listInProgressByUser(userId)) {
            if (taskId != row.getTaskId()) {
                continue;
            }
            if (cycleKey.equals(row.getCycleKey())) {
                return row;
            }
            if (fallback == null) {
                fallback = row;
            }
        }
        return fallback;
    }
}
