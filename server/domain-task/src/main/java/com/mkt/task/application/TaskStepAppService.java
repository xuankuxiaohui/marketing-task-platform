package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.engine.StepAdvanceResult;
import com.mkt.task.engine.StepEngine;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStepAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final StepEngine engine;
    private final Clock clock;

    @Autowired
    public TaskStepAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskProgressReportStore reports,
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
                reports,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                events.getIfAvailable(),
                rewards.getIfAvailable(),
                clock,
                settings);
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
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.risk = risk;
        this.clock = clock;
        this.engine = new StepEngine(instances, reports, events, clock, settings, rewards);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskClickResponse click(
            long instanceId, String stepCode, long userId, String ip, String deviceId, String platform) {
        TaskInstanceEntity instance = instances.getById(instanceId);
        if (instance == null || instance.getUserId() == null || instance.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        rejectIfFrozen(userId, TaskErrorCodes.INSTANCE_FROZEN);
        TaskInstanceStepEntity step = requireStep(instanceId, stepCode);
        StepAdvanceResult result =
                engine.click(instance, step, snapshotOf(instance), attrs(userId), crowds(userId), ip, deviceId);
        return toClick(result, platform);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskCallbackResponse callback(InternalCallbackCommand command) {
        TaskInstanceEntity instance = locate(command.instanceId(), command.userId(), command.taskCode(), command.cycleKey());
        rejectIfFrozen(instance.getUserId(), TaskErrorCodes.ACCOUNT_RESTRICTED);
        TaskInstanceStepEntity step = requireStep(instance.getId(), command.stepCode());
        StepAdvanceResult result = engine.callback(
                instance, step, snapshotOf(instance), attrs(instance.getUserId()), crowds(instance.getUserId()), command.bizNo());
        TaskInstanceEntity fresh = result.instance();
        TaskInstanceStepEntity after = result.step();
        return new TaskCallbackResponse(
                fresh.getId(), after.getStepCode(), after.getStatus(), fresh.getStatus());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskProgressResponse progress(InternalProgressCommand command) {
        if (command.reportId() == null || command.reportId().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (command.value() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskInstanceEntity instance = locate(command.instanceId(), command.userId(), command.taskCode(), command.cycleKey());
        rejectIfFrozen(instance.getUserId(), TaskErrorCodes.ACCOUNT_RESTRICTED);
        TaskInstanceStepEntity step = requireStep(instance.getId(), command.stepCode());
        StepAdvanceResult result = engine.progress(
                instance,
                step,
                snapshotOf(instance),
                attrs(instance.getUserId()),
                crowds(instance.getUserId()),
                command.value(),
                command.reportId().trim());
        TaskInstanceStepEntity after = result.step();
        int current = after.getProgressCurrent() == null ? 0 : after.getProgressCurrent();
        return new TaskProgressResponse(
                result.instance().getId(), after.getStepCode(), current, result.progressTarget(), after.getStatus());
    }

    private TaskInstanceEntity locate(Long instanceId, Long userId, String taskCode, String cycleKey) {
        if (instanceId != null) {
            TaskInstanceEntity row = instances.getById(instanceId);
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
        return row;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resumeAfterGrant(String sourceId) {
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
        if (step == null) {
            return;
        }
        TaskInstanceEntity instance = instances.getById(step.getInstanceId());
        if (instance == null) {
            return;
        }
        engine.resumeFromReward(
                instance, step, snapshotOf(instance), attrs(instance.getUserId()), crowds(instance.getUserId()));
    }

    private TaskInstanceStepEntity requireStep(long instanceId, String stepCode) {
        if (stepCode == null || stepCode.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        TaskInstanceStepEntity step = instances.getStep(instanceId, stepCode.trim());
        if (step == null) {
            throw new BusinessException(TaskErrorCodes.STEP_NOT_FOUND);
        }
        return step;
    }

    private void rejectIfFrozen(long userId, TaskErrorCodes code) {
        if (blacklisted(userId)) {
            throw new BusinessException(code);
        }
    }

    private boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        UserRiskSummary summary = risk.userSummary(userId);
        return summary != null && summary.listStatus().contains(RiskListType.BLACK);
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

    private TaskClickResponse toClick(StepAdvanceResult result, String platform) {
        TaskInstanceEntity instance = result.instance();
        SnapshotContent snapshot = snapshotOf(instance);
        CurrentStepView next = currentStep(instance.getId(), snapshot, platform);
        List<RewardFeedbackView> feedback = InstanceStatuses.COMPLETED.equals(instance.getStatus())
                ? result.rewardFeedback()
                : List.of();
        return new TaskClickResponse(
                instance.getId(), result.step().getStatus(), instance.getStatus(), next, feedback);
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
