package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
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
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.domain.ExpireAtCalculator;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.engine.ClaimEnterEngine;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskClaimAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final TaskMutexGroupStore mutexGroups;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final EventPublisher events;
    private final Clock clock;
    private final TaskSettings settings;
    private final ClaimEnterEngine enter;

    @Autowired
    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskMutexGroupStore mutexGroups,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            ObjectProvider<EventPublisher> events,
            ObjectProvider<RewardPort> rewards,
            Clock clock,
            TaskSettings settings) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                mutexGroups,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                events.getIfAvailable(),
                rewards.getIfAvailable(),
                clock,
                settings);
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
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.mutexGroups = mutexGroups;
        this.users = users;
        this.risk = risk;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        this.enter = new ClaimEnterEngine(instances, events, clock, settings, rewards);
    }

    @Transactional
    public TaskStartResponse start(long taskId, long userId, String ip, String deviceId, String platform) {
        if (users == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        UserAttributes attrs = users.lockAndGet(userId);
        if (attrs.accountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(TaskErrorCodes.ACCOUNT_DISABLED);
        }
        TaskDefinitionEntity definition = definitions.getById(taskId);
        if (definition == null || definition.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        Instant now = clock.instant();
        String cycleKey = CycleKeyResolver.resolve(
                definition.getCycleType(),
                definition.getCronExpr(),
                TaskTime.toInstant(definition.getSpecialStart()),
                TaskTime.toInstant(definition.getSpecialEnd()),
                now);
        TaskInstanceEntity existing = instances.getByUserTaskCycle(userId, taskId, cycleKey);
        if (existing != null) {
            return toStart(existing, platform);
        }
        SnapshotContent snapshot = requireSnapshot(definition);
        VisibilityResult visibility = visibility(definition, snapshot, userId, attrs, now);
        if (!visibility.visible()) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        checkRisk(userId, ip, deviceId);
        checkMutex(definition, userId, cycleKey);
        checkDailyLimit(userId, now);
        Inserted inserted = insertInstance(definition, snapshot, userId, cycleKey, now);
        if (!inserted.created()) {
            return toStart(inserted.row(), platform);
        }
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        enter.enter(inserted.row(), snapshot, attrs, resolver);
        if (events != null) {
            events.append(
                    EventCodes.TASK_INSTANCE_START,
                    "task_instance",
                    String.valueOf(inserted.row().getId()),
                    Map.of("instanceId", inserted.row().getId(), "taskId", taskId, "userId", userId));
        }
        TaskInstanceEntity fresh = instances.getById(inserted.row().getId());
        return toStart(fresh == null ? inserted.row() : fresh, platform);
    }

    private Inserted insertInstance(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            String cycleKey,
            Instant now) {
        Instant cycleEnd = CycleKeyResolver.cycleEnd(
                definition.getCycleType(),
                definition.getCronExpr(),
                TaskTime.toInstant(definition.getSpecialStart()),
                TaskTime.toInstant(definition.getSpecialEnd()),
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
        row.setSimulated(0);
        row.setCreatedAt(TaskTime.toUtc(now));
        try {
            instances.insert(row);
        } catch (DuplicateKeyException ex) {
            TaskInstanceEntity winner = instances.getByUserTaskCycle(userId, definition.getId(), cycleKey);
            if (winner != null) {
                return new Inserted(winner, false);
            }
            throw ex;
        }
        return new Inserted(row, true);
    }

    private record Inserted(TaskInstanceEntity row, boolean created) {}

    private void checkRisk(long userId, String ip, String deviceId) {
        if (risk == null) {
            return;
        }
        String resolvedIp = ip == null || ip.isBlank() ? "0.0.0.0" : ip;
        RiskVerdict verdict = risk.check(RiskScene.CLAIM, new RiskSubject(userId, resolvedIp, deviceId, null));
        if (verdict.action() == RiskAction.REJECT || verdict.action() == RiskAction.SILENT_REJECT) {
            throw new BusinessException(TaskErrorCodes.RISK_BLOCKED_GENERIC);
        }
    }

    private void checkMutex(TaskDefinitionEntity definition, long userId, String cycleKey) {
        if (definition.getMutexGroupId() == null) {
            return;
        }
        List<Long> groupTaskIds = definitions.listIdsByMutexGroup(definition.getMutexGroupId());
        if (groupTaskIds == null || groupTaskIds.isEmpty()) {
            return;
        }
        TaskMutexGroupEntity group = mutexGroups.getById(definition.getMutexGroupId());
        String matchCycle = group != null && group.crossCycleFlag() ? null : cycleKey;
        if (instances.existsInProgress(userId, groupTaskIds, matchCycle)) {
            throw new BusinessException(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
        }
    }

    private void checkDailyLimit(long userId, Instant now) {
        ZonedDateTime zoned = now.atZone(CycleKeyResolver.ZONE);
        LocalDateTime from = TaskTime.toUtc(zoned.toLocalDate().atStartOfDay(CycleKeyResolver.ZONE).toInstant());
        LocalDateTime to = TaskTime.toUtc(
                zoned.toLocalDate().plusDays(1).atStartOfDay(CycleKeyResolver.ZONE).toInstant());
        int today = instances.countToday(userId, from, to);
        if (today >= settings.dailyLimitPerUser()) {
            throw new BusinessException(TaskErrorCodes.CLAIM_DAILY_LIMIT);
        }
    }

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

    private SnapshotContent requireSnapshot(TaskDefinitionEntity definition) {
        if (!DefinitionStatuses.PUBLISHED.equals(definition.getStatus())
                || definition.getVersion() == null
                || definition.getVersion() < 1) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(definition.getId(), definition.getVersion());
        if (snap == null || snap.getContent() == null) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
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
