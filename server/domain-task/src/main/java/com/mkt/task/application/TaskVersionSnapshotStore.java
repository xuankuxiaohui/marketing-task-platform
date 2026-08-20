package com.mkt.task.application;

import com.mkt.task.entity.TaskVersionSnapshotEntity;
import java.util.List;

public interface TaskVersionSnapshotStore {

    int insert(TaskVersionSnapshotEntity entity);

    TaskVersionSnapshotEntity getById(long id);

    TaskVersionSnapshotEntity getByTaskAndVersion(long taskId, int version);

    List<TaskVersionSnapshotEntity> listByTaskId(long taskId);
}
