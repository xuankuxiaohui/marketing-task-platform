package com.mkt.task.application;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.TaskStepSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step source extracted from {@link TaskPortalAppService} (R23-2).
 * Move-only: sourceForStep semantics unchanged.
 */
@Component
public class PortalSourceForStep {

    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;

    @Autowired
    public PortalSourceForStep(TaskVersionSnapshotStore snapshots, TaskInstanceStore instances) {
        this.snapshots = snapshots;
        this.instances = instances;
    }

    TaskStepSource sourceForStep(long stepId) {
        TaskInstanceStepEntity step = instances.getStepById(stepId);
        if (step == null || step.getInstanceId() == null) {
            return null;
        }
        TaskInstanceEntity instance = instances.getById(step.getInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            return null;
        }
        TaskVersionSnapshotEntity snap =
                instance.getSnapshotId() == null ? null : snapshots.getById(instance.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null || content.name() == null || content.name().isBlank()
                ? instance.getTaskCode()
                : content.name();
        return new TaskStepSource(instance.getTaskId(), name);
    }
}
