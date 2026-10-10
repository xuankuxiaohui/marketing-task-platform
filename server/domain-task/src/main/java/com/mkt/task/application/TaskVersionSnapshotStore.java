package com.mkt.task.application;

import com.mkt.task.entity.TaskVersionSnapshotEntity;
import java.util.List;

public interface TaskVersionSnapshotStore {

    int insert(TaskVersionSnapshotEntity entity);

    TaskVersionSnapshotEntity getById(long id);

    List<TaskVersionSnapshotEntity> listByIds(List<Long> ids);

    TaskVersionSnapshotEntity getByTaskAndVersion(long taskId, int version);

    /** Bounded batch read for portal catalog (F01). Empty keys → empty list. */
    List<TaskVersionSnapshotEntity> listByTaskAndVersions(List<TaskVersionKey> keys);

    List<TaskVersionSnapshotEntity> listByTaskId(long taskId);
}
