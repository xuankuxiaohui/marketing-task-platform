package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.engine.StepAdvanceResult;
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
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Click entry extracted from {@link TaskStepAppService} (R20-1).
 * Move-only: click semantics unchanged.
 * {@code snapshotOf}, {@code attrs}, and {@code crowds} are copied so the moved body stays verbatim.
 */
@Component
public class StepClick {

    private final TaskInstanceStore instances;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final TaskStepGuards guards;
    private final TaskStepViews views;
    private final StepEngine engine;

    @Autowired
    public StepClick(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            TaskStepGuards guards,
            TaskStepViews views,
            TaskProgressReportStore reports,
            Clock clock,
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
                guards,
                views,
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

    StepClick(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            TaskCrowdStore crowds,
            UserAttributePort users,
            TaskStepGuards guards,
            TaskStepViews views,
            StepEngine engine) {
        this.instances = instances;
        this.snapshots = snapshots;
        this.crowds = crowds;
        this.users = users;
        this.guards = guards;
        this.views = views;
        this.engine = engine;
    }

    TaskClickResponse click(
            long instanceId, String stepCode, long userId, String ip, String deviceId, String platform) {
        TaskInstanceEntity instance = instances.getByIdForUpdate(instanceId);
        if (instance == null || instance.getUserId() == null || instance.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        guards.rejectIfFrozen(userId, TaskErrorCodes.INSTANCE_FROZEN);
        TaskInstanceStepEntity step = guards.requireStep(instanceId, stepCode);
        StepAdvanceResult result =
                engine.click(instance, step, snapshotOf(instance), attrs(userId), crowds(userId), ip, deviceId);
        return views.toClick(result, platform);
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
