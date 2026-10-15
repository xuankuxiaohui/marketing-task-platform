package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.InternalProgressCommand;
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
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Progress entry extracted from {@link TaskStepAppService} (R20-3).
 * Move-only: progress semantics unchanged.
 * {@code crowds} is copied so the moved body stays verbatim.
 */
@Component
public class StepProgress {

    private final TaskStepPreload preload;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final TaskStepGuards guards;
    private final StepEngine engine;

    @Autowired
    public StepProgress(
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

    StepProgress(
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

    TaskProgressResponse progress(InternalProgressCommand command) {
        if (command.reportId() == null || command.reportId().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (command.value() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskInstanceEntity soft = preload.locateSoft(command.instanceId(), command.userId(), command.taskCode(), command.cycleKey());
        guards.rejectIfFrozen(soft.getUserId(), TaskErrorCodes.ACCOUNT_RESTRICTED);
        SnapshotContent snapshot = preload.cachedSnapshot(soft);
        UserAttributes userAttrs = preload.cachedAttrs(soft.getUserId());
        CrowdResolver crowdResolver = crowds(soft.getUserId());
        TaskInstanceEntity instance = preload.lockInstance(soft);
        TaskInstanceStepEntity step = guards.requireStep(instance.getId(), command.stepCode());
        StepAdvanceResult result = engine.progress(
                instance,
                step,
                snapshot,
                userAttrs,
                crowdResolver,
                command.value(),
                command.reportId().trim());
        TaskInstanceStepEntity after = result.step();
        int current = after.getProgressCurrent() == null ? 0 : after.getProgressCurrent();
        return new TaskProgressResponse(
                result.instance().getId(), after.getStepCode(), current, result.progressTarget(), after.getStatus());
    }

    private CrowdResolver crowds(long userId) {
        return new StoreCrowdResolver(crowds, users, userId);
    }
}
