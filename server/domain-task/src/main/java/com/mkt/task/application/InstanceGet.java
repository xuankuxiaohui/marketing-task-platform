package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceStepView;
import com.mkt.task.response.InstanceEventView;
import com.mkt.task.support.TaskErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admin instance detail extracted from {@link TaskInstanceAppService} (R24-2).
 * Move-only: get semantics unchanged.
 */
@Component
public class InstanceGet {

    private final TaskInstanceStore instances;
    private final InstanceViews views;
    private final InstanceEventStore events;

    @Autowired
    public InstanceGet(
            TaskInstanceStore instances,
            InstanceViews views,
            ObjectProvider<InstanceEventStore> events) {
        this(instances, views, events.getIfAvailable());
    }

    InstanceGet(TaskInstanceStore instances, InstanceViews views, InstanceEventStore events) {
        this.instances = instances;
        this.views = views;
        this.events = events;
    }

    AdminInstanceDetailResponse get(long id) {
        TaskInstanceEntity row = instances.getById(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        SnapshotContent snapshot = views.snapshotOf(row);
        List<AdminInstanceStepView> steps = new ArrayList<>();
        for (TaskInstanceStepEntity step : instances.listSteps(id)) {
            TaskStepCommand def = snapshot == null ? null : SnapshotViews.step(snapshot, step.getStepCode());
            Integer target = def == null ? null : def.progressTarget();
            steps.add(new AdminInstanceStepView(
                    step.getStepCode(),
                    step.getType(),
                    step.getStatus(),
                    step.getProgressCurrent() == null ? 0 : step.getProgressCurrent(),
                    target,
                    TaskTime.toInstant(step.getActivatedAt()),
                    TaskTime.toInstant(step.getCompletedAt())));
        }
        List<InstanceEventView> timeline =
                events == null ? List.of() : events.listByInstance(id, row.getUserId());
        return new AdminInstanceDetailResponse(views.toView(row), steps, timeline);
    }
}
