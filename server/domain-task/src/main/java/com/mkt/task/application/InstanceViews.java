package com.mkt.task.application;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.AdminInstanceView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Instance view helpers extracted from {@link TaskInstanceAppService} (R12-1).
 * Move-only: toView / snapshotOf semantics unchanged.
 */
@Component
public class InstanceViews {

    private final TaskVersionSnapshotStore snapshots;

    @Autowired
    public InstanceViews(TaskVersionSnapshotStore snapshots) {
        this.snapshots = snapshots;
    }

    SnapshotContent snapshotOf(TaskInstanceEntity row) {
        if (row.getSnapshotId() == null || snapshots == null) {
            return null;
        }
        TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    static AdminInstanceView toView(TaskInstanceEntity row) {
        return new AdminInstanceView(
                row.getId(),
                row.getTaskId(),
                row.getTaskCode(),
                row.getUserId(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getCycleKey(),
                row.getStatus(),
                row.getAbandonSource(),
                TaskTime.toInstant(row.getAbandonedAt()),
                TaskTime.toInstant(row.getExpireAt()),
                TaskTime.toInstant(row.getStartedAt()),
                TaskTime.toInstant(row.getCompletedAt()),
                row.getCostSeconds(),
                row.getSimulated() == null ? 0 : row.getSimulated());
    }
}
