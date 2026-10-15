package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.engine.StepEngine;
import com.mkt.task.engine.StepEngineAdvance;
import com.mkt.task.engine.StepEngineCascadeSupport;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.engine.StepEngineReward;
import com.mkt.task.engine.StepEngineViews;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grant-resume entry extracted from {@link TaskStepAppService} (R20-4).
 * Move-only: resumeAfterGrant semantics unchanged.
 * {@code snapshotOf}, {@code attrs}, and {@code crowds} are copied so the moved body stays verbatim.
 */
@Component
public class StepResume {

    private final TaskInstanceStore instances;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final Clock clock;
    private final StepEngine engine;

    @Autowired
    public StepResume(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            Clock clock,
            TaskProgressReportStore reports,
            TaskSettings settings,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps) {
        this(
                instances,
                snapshots,
                crowds,
                users.getIfAvailable(),
                clock,
                new StepEngine(
                        instances,
                        reports,
                        clock,
                        settings,
                        rewardOps,
                        cascadeOps,
                        advanceOps,
                        engineViews,
                        expireOps));
    }

    StepResume(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            TaskCrowdStore crowds,
            UserAttributePort users,
            Clock clock,
            StepEngine engine) {
        this.instances = instances;
        this.snapshots = snapshots;
        this.crowds = crowds;
        this.users = users;
        this.clock = clock;
        this.engine = engine;
    }

    void resumeAfterGrant(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        long stepId;
        try {
            stepId = Long.parseLong(sourceId.trim());
        } catch (NumberFormatException ex) {
            return;
        }
        TaskInstanceStepEntity step = instances.getStepById(stepId);
        if (step == null || !StepStatuses.ACTIVE.equals(step.getStatus())
                || !StepTypes.REWARD.equals(step.getType())) {
            return;
        }
        TaskInstanceEntity instance = instances.getByIdForUpdate(step.getInstanceId());
        if (instance == null || InstanceStatuses.terminal(instance.getStatus())
                || StepEngineExpire.expired(instance, clock.instant())) {
            return;
        }
        step = instances.getStepById(stepId);
        if (step == null) {
            return;
        }
        engine.resumeFromReward(
                instance, step, snapshotOf(instance), attrs(instance.getUserId()), crowds(instance.getUserId()));
    }

    private SnapshotContent snapshotOf(TaskInstanceEntity instance) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private UserAttributes attrs(long userId) {
        if (users == null) {
            return UserAttributes.notFound();
        }
        return users.attributes(userId);
    }

    private CrowdResolver crowds(long userId) {
        return new StoreCrowdResolver(crowds, users, userId);
    }
}
