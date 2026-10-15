package com.mkt.task.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.engine.ClaimEnterEngine;
import com.mkt.task.engine.StepEngineAdvance;
import com.mkt.task.engine.StepEngineCascadeSupport;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.engine.StepEngineReward;
import com.mkt.task.engine.StepEngineViews;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Start entry extracted from {@link TaskClaimAppService} (R21-1).
 * Move-only: start semantics unchanged.
 */
@Component
public class ClaimStart {

    private final TaskDefinitionStore definitions;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final EventPublisher events;
    private final Clock clock;
    private final ClaimChecks checks;
    private final ClaimInsert insert;
    private final ClaimViews views;
    private final ClaimEnterEngine enter;

    @Autowired
    public ClaimStart(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps) {
        this(
                definitions,
                instances,
                crowds,
                users.getIfAvailable(),
                events.getIfAvailable(),
                clock,
                checks,
                insert,
                views,
                new ClaimEnterEngine(
                        instances,
                        events.getIfAvailable(),
                        clock,
                        settings,
                        rewardOps,
                        cascadeOps,
                        advanceOps,
                        engineViews,
                        expireOps));
    }

    ClaimStart(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            ClaimEnterEngine enter) {
        this.definitions = definitions;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.checks = checks;
        this.insert = insert;
        this.views = views;
        this.enter = enter;
    }

    TaskStartResponse start(long taskId, long userId, String ip, String deviceId, String platform) {
        return start(taskId, userId, ip, deviceId, platform, false);
    }

    TaskStartResponse start(
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
        SnapshotContent snapshot = views.snapshotOf(definition);
        String cycleKey = views.cycleKeyOf(snapshot, definition, now);
        TaskInstanceEntity existing = instances.getByUserTaskCycle(userId, taskId, cycleKey);
        if (existing != null) {
            boolean existingSimulated = existing.getSimulated() != null && existing.getSimulated() == 1;
            if (simulated && !existingSimulated) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
            }
            return views.toStart(existing, platform);
        }
        if (snapshot == null) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        VisibilityResult visibility = views.visibility(definition, snapshot, userId, attrs, now);
        if (!visibility.visible()) {
            throw new BusinessException(TaskErrorCodes.CLAIM_NOT_VISIBLE);
        }
        checks.checkRisk(userId, ip, deviceId, simulated);
        checks.checkMutex(snapshot, userId, cycleKey);
        checks.checkDailyLimit(userId, now);
        ClaimInsert.Inserted inserted = insert.insertInstance(definition, snapshot, userId, cycleKey, now, simulated);
        if (!inserted.created()) {
            return views.toStart(inserted.row(), platform);
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
        return views.toStart(fresh == null ? inserted.row() : fresh, platform);
    }
}
