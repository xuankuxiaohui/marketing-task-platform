package com.mkt.task.engine;

import com.mkt.kernel.BusinessException;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.engine.StepEngine.Entry;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.support.TaskErrorCodes;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * View helpers extracted from {@link StepEngine} (R10-1).
 * Move-only: snapshotOf / reloadStep / requireEntryType semantics unchanged.
 * {@code progressTarget} and {@code stepDef} move with {@code snapshotOf} so that body stays verbatim.
 */
@Component
public class StepEngineViews {

    private final TaskInstanceStore instances;

    @Autowired
    public StepEngineViews(TaskInstanceStore instances) {
        this.instances = instances;
    }

    static void requireEntryType(Entry entry, TaskInstanceStepEntity step) {
        String expected = switch (entry) {
            case CLICK -> StepTypes.CLICK;
            case CALLBACK -> StepTypes.CALLBACK;
            case PROGRESS -> StepTypes.PROGRESS;
            case ENTER -> null;
        };
        if (expected != null && !expected.equals(step.getType())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
    }

    StepAdvanceResult snapshotOf(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            List<RewardFeedbackView> feedback,
            boolean idempotent) {
        TaskInstanceEntity fresh = instances.getById(instance.getId());
        TaskInstanceEntity row = fresh == null ? instance : fresh;
        return new StepAdvanceResult(row, step, progressTarget(snapshot, step.getStepCode()), feedback, idempotent);
    }

    TaskInstanceStepEntity reloadStep(long instanceId, String stepCode) {
        return instances.getStep(instanceId, stepCode);
    }

    private static Integer progressTarget(SnapshotContent snapshot, String stepCode) {
        TaskStepCommand def = stepDef(snapshot, stepCode);
        return def == null ? null : def.progressTarget();
    }

    private static TaskStepCommand stepDef(SnapshotContent snapshot, String stepCode) {
        if (snapshot == null || snapshot.steps() == null || stepCode == null) {
            return null;
        }
        for (TaskStepCommand step : snapshot.steps()) {
            if (stepCode.equals(step.code())) {
                return step;
            }
        }
        return null;
    }
}
