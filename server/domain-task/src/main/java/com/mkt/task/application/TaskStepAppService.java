package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.engine.StepEngine;
import com.mkt.task.engine.StepEngineAdvance;
import com.mkt.task.engine.StepEngineCascadeSupport;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.engine.StepEngineReward;
import com.mkt.task.engine.StepEngineViews;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStepAppService {

    private final TaskDefinitionStore definitions;
    private final TaskInstanceStore instances;
    private final StepClick stepClick;
    private final StepCallback stepCallback;
    private final StepProgress stepProgress;
    private final StepResume stepResume;

    @Autowired
    public TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            StepClick stepClick,
            StepCallback stepCallback,
            StepProgress stepProgress,
            StepResume stepResume) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users.getIfAvailable(),
                events.getIfAvailable(),
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                stepClick,
                stepCallback,
                stepProgress,
                stepResume);
    }

    public TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
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
                reports,
                users,
                events,
                clock,
                settings,
                new TaskStepPreload(definitions, snapshots, instances, users),
                new TaskStepViews(snapshots, instances),
                new TaskStepGuards(instances, risk),
                new StepEngineReward(instances, events, clock, rewards));
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                new StepEngineCascadeSupport(instances, events, clock, settings, rewardOps));
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                new StepEngineAdvance(instances, clock, cascadeOps));
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                new StepEngineViews(instances),
                new StepEngineExpire());
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
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
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                null);
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            StepClick stepClick) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                stepClick,
                null);
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            StepClick stepClick,
            StepCallback stepCallback) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                stepClick,
                stepCallback,
                null);
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            StepClick stepClick,
            StepCallback stepCallback,
            StepProgress stepProgress) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                reports,
                users,
                events,
                clock,
                settings,
                preload,
                views,
                guards,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                stepClick,
                stepCallback,
                stepProgress,
                null);
    }

    private TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            TaskStepPreload preload,
            TaskStepViews views,
            TaskStepGuards guards,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            StepClick stepClick,
            StepCallback stepCallback,
            StepProgress stepProgress,
            StepResume stepResume) {
        this.definitions = definitions;
        this.instances = instances;
        StepEngine engine = new StepEngine(
                instances, reports, clock, settings, rewardOps, cascadeOps, advanceOps, engineViews, expireOps);
        // Manual/IT chain passes null and shares this engine. Production passes the injected collaborators.
        this.stepClick = stepClick != null
                ? stepClick
                : new StepClick(instances, snapshots, crowds, users, guards, views, engine);
        this.stepCallback = stepCallback != null
                ? stepCallback
                : new StepCallback(preload, crowds, users, guards, engine);
        this.stepProgress = stepProgress != null
                ? stepProgress
                : new StepProgress(preload, crowds, users, guards, engine);
        this.stepResume = stepResume != null
                ? stepResume
                : new StepResume(instances, snapshots, crowds, users, clock, engine);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskClickResponse click(
            long instanceId, String stepCode, long userId, String ip, String deviceId, String platform) {
        return stepClick.click(instanceId, stepCode, userId, ip, deviceId, platform);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskCallbackResponse callback(InternalCallbackCommand command) {
        return stepCallback.callback(command);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskProgressResponse progress(InternalProgressCommand command) {
        return stepProgress.progress(command);
    }

    /** Legacy locate with FOR UPDATE — used by click/resume paths unchanged. */
    private TaskInstanceEntity locate(Long instanceId, Long userId, String taskCode, String cycleKey) {
        if (instanceId != null) {
            TaskInstanceEntity row = instances.getByIdForUpdate(instanceId);
            if (row == null) {
                throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
            }
            return row;
        }
        if (userId == null || taskCode == null || taskCode.isBlank() || cycleKey == null || cycleKey.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskDefinitionEntity definition = definitions.getByCode(taskCode.trim());
        if (definition == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        TaskInstanceEntity row = instances.getByUserTaskCycle(userId, definition.getId(), cycleKey.trim());
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        TaskInstanceEntity locked = instances.getByIdForUpdate(row.getId());
        if (locked == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        return locked;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resumeAfterGrant(String sourceId) {
        stepResume.resumeAfterGrant(sourceId);
    }
}
