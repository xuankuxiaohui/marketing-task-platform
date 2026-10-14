package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.ExpireAtCalculator;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.engine.ClaimEnterEngine;
import com.mkt.task.engine.StepEngineAdvance;
import com.mkt.task.engine.StepEngineCascadeSupport;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.engine.StepEngineReward;
import com.mkt.task.engine.StepEngineViews;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskClaimAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final EventPublisher events;
    private final Clock clock;
    private final TaskSettings settings;
    private final ClaimChecks checks;
    private final ClaimEnterEngine enter;

    @Autowired
    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users.getIfAvailable(),
                events.getIfAvailable(),
                clock,
                settings,
                checks,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps);
    }

    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskMutexGroupStore mutexGroups,
            UserAttributePort users,
            RiskCheckPort risk,
            EventPublisher events,
            Clock clock,
            TaskSettings settings) {
        this(definitions, snapshots, instances, crowds, mutexGroups, users, risk, events, null, clock, settings);
    }

    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskMutexGroupStore mutexGroups,
            UserAttributePort users,
            RiskCheckPort risk,
            EventPublisher events,
            RewardPort rewards,
            Clock clock,
            TaskSettings settings) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                new ClaimChecks(mutexGroups, instances, risk, settings),
                new StepEngineReward(instances, events, clock, rewards));
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            StepEngineReward rewardOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                rewardOps,
                new StepEngineCascadeSupport(instances, events, clock, settings, rewardOps));
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                rewardOps,
                cascadeOps,
                new StepEngineAdvance(instances, clock, cascadeOps));
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                rewardOps,
                cascadeOps,
                advanceOps,
                new StepEngineViews(instances),
                new StepEngineExpire());
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        this.checks = checks;
        this.enter = new ClaimEnterEngine(
                instances, events, clock, settings, rewardOps, cascadeOps, advanceOps, engineViews, expireOps);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskStartResponse start(long taskId, long userId, String ip, String deviceId, String platform) {
        return start(taskId, userId, ip, deviceId, platform, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskStartResponse start(
            long taskId, long userId, String ip, String deviceId, String platform, boolean simulated) {
        if (users == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        // Definition admission precedes the user lock: readers may run together,
        // while publish/offline waits for all admitted claims to finish.
        TaskDefinitionEntity definition = definitions.getByIdForShare(taskId);
        UserAttributes attrs = users.lockAndGet(userId);
        if (attrs.accountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(TaskErrorCodes.ACCOUNT_DISABLED);
        }
        if (definition == null || definition.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        Instant now = clock.instant();
        SnapshotContent snapshot = snapshotOf(definition);
        String cycleKey = cycleKeyOf(snapshot, definition, now);
        TaskInstanceEntity existing = instances.getByUserTaskCycle(userId, taskId, cycleKey);
        if (existing != null) {
            boolean existingSimulated = existing.getSimulated() != null && existing.getSimulated() == 1;
            if (simulated && !existingSimulated) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
            }
            return toStart(existing, platform);
        }
        if (snapshot == null) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        VisibilityResult visibility = visibility(definition, snapshot, userId, attrs, now);
        if (!visibility.visible()) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        checks.checkRisk(userId, ip, deviceId, simulated);
        checks.checkMutex(snapshot, userId, cycleKey);
        checks.checkDailyLimit(userId, now);
        Inserted inserted = insertInstance(definition, snapshot, userId, cycleKey, now, simulated);
        if (!inserted.created()) {
            return toStart(inserted.row(), platform);
        }
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        enter.enter(inserted.row(), snapshot, attrs, resolver, ip, deviceId);
        if (events != null) {
            events.append(
                    EventCodes.TASK_INSTANCE_START,
                    "task_instance",
                    String.valueOf(inserted.row().getId()),
                    Map.of(
                            "instanceId", inserted.row().getId(),
                            "taskId", taskId,
                            "userId", userId,
                            "simulated", simulated));
        }
        TaskInstanceEntity fresh = instances.getById(inserted.row().getId());
        return toStart(fresh == null ? inserted.row() : fresh, platform);
    }

    private Inserted insertInstance(
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

    private record Inserted(TaskInstanceEntity row, boolean created) {}

    private VisibilityResult visibility(
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

    private SnapshotContent snapshotOf(TaskDefinitionEntity definition) {
        if (definition.getVersion() == null || definition.getVersion() < 1) {
            return null;
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(definition.getId(), definition.getVersion());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private static String cycleKeyOf(SnapshotContent snapshot, TaskDefinitionEntity definition, Instant now) {
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

    private TaskStartResponse toStart(TaskInstanceEntity instance, String platform) {
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
