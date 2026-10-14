package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.engine.StepAdvanceResult;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.response.TaskClickResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * View mapping helpers extracted from {@link TaskStepAppService} (R8-2).
 * Move-only: toClick / currentStep semantics unchanged.
 * {@code toClick} still calls {@code snapshotOf}; that loader is copied so the moved body stays verbatim.
 */
@Component
public class TaskStepViews {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;

    @Autowired
    public TaskStepViews(TaskVersionSnapshotStore snapshots, TaskInstanceStore instances) {
        this.snapshots = snapshots;
        this.instances = instances;
    }

    TaskClickResponse toClick(StepAdvanceResult result, String platform) {
        TaskInstanceEntity instance = result.instance();
        SnapshotContent snapshot = snapshotOf(instance);
        CurrentStepView next = currentStep(instance.getId(), snapshot, platform);
        List<RewardFeedbackView> feedback = InstanceStatuses.COMPLETED.equals(instance.getStatus())
                ? result.rewardFeedback()
                : List.of();
        return new TaskClickResponse(
                instance.getId(), result.step().getStatus(), instance.getStatus(), next, feedback);
    }

    CurrentStepView currentStep(long instanceId, SnapshotContent snapshot, String platform) {
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

    private SnapshotContent snapshotOf(TaskInstanceEntity instance) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }
}
