package com.mkt.task.application;

import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.mapper.TaskVersionSnapshotMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskVersionSnapshotStore implements TaskVersionSnapshotStore {

    private final TaskVersionSnapshotMapper mapper;

    public MybatisTaskVersionSnapshotStore(TaskVersionSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(TaskVersionSnapshotEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public TaskVersionSnapshotEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public TaskVersionSnapshotEntity getByTaskAndVersion(long taskId, int version) {
        return mapper.selectByTaskAndVersion(taskId, version);
    }

    @Override
    public List<TaskVersionSnapshotEntity> listByTaskId(long taskId) {
        return mapper.selectByTaskId(taskId);
    }
}
