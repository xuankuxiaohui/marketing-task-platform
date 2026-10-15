package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.task.command.InternalCallbackCommand;
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
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Callback entry extracted from {@link TaskStepAppService} (R20-2).
 * Move-only: callback semantics unchanged.
 * {@code crowds} is copied so the moved body stays verbatim.
 */
@Component
public class StepCallback {

    private final TaskStepPreload preload;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final TaskStepGuards guards;
    private final StepEngine engine;

    @Autowired
    public StepCallback(
            TaskStepPreload preload,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            TaskStepGuards guards,
            TaskInstanceStore instances,
            TaskProgressReportStore reports,
            Clock clock,
            TaskSettings settings,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps) {
        this(
                preload,
                crowds,
                users.getIfAvailable(),
                guards,
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

    StepCallback(
            TaskStepPreload preload,
            TaskCrowdStore crowds,
            UserAttributePort users,
            TaskStepGuards guards,
            StepEngine engine) {
        this.preload = preload;
        this.crowds = crowds;
        this.users = users;
        this.guards = guards;
        this.engine = engine;
    }

    TaskCallbackResponse callback(InternalCallbackCommand command) {
        // Hold-lock knife: attrs/crowds/snapshot (+ frozen check) BEFORE getByIdForUpdate.
        // Lock window: CAS + report/outbox same-TX only (DEC-002 unchanged; no sync evt wait).
        TaskInstanceEntity soft = preload.locateSoft(command.instanceId(), command.userId(), command.taskCode(), command.cycleKey());
        guards.rejectIfFrozen(soft.getUserId(), TaskErrorCodes.ACCOUNT_RESTRICTED);
        SnapshotContent snapshot = preload.cachedSnapshot(soft);
        UserAttributes userAttrs = preload.cachedAttrs(soft.getUserId());
        CrowdResolver crowdResolver = crowds(soft.getUserId());
        TaskInstanceEntity instance = preload.lockInstance(soft);
        TaskInstanceStepEntity step = guards.requireStep(instance.getId(), command.stepCode());
        StepAdvanceResult result =
                engine.callback(instance, step, snapshot, userAttrs, crowdResolver, command.bizNo());
        TaskInstanceEntity fresh = result.instance();
        TaskInstanceStepEntity after = result.step();
        return new TaskCallbackResponse(
                fresh.getId(), after.getStepCode(), after.getStatus(), fresh.getStatus());
    }

    private CrowdResolver crowds(long userId) {
        return new StoreCrowdResolver(crowds, users, userId);
    }
}
