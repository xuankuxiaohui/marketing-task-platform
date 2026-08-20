package com.mkt.task.application;

import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.mapper.TaskDefinitionMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskDefinitionStore implements TaskDefinitionStore {

    private final TaskDefinitionMapper mapper;

    public MybatisTaskDefinitionStore(TaskDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TaskDefinitionEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public TaskDefinitionEntity getByCode(String code) {
        return mapper.selectByCode(code);
    }

    @Override
    public int insert(TaskDefinitionEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(TaskDefinitionEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public long countByQuery(String code, String name, String status, String category) {
        return mapper.selectCountByQuery(code, name, status, category);
    }

    @Override
    public List<TaskDefinitionEntity> listByQuery(
            String code, String name, String status, String category, long offset, int limit) {
        return mapper.selectByQuery(code, name, status, category, offset, limit);
    }

    @Override
    public List<String> cycleTypesInMutexGroup(long mutexGroupId, Long excludeTaskId) {
        return mapper.selectCycleTypesByMutexGroup(mutexGroupId, excludeTaskId);
    }

    @Override
    public int countByMutexGroup(long mutexGroupId) {
        return mapper.countByMutexGroup(mutexGroupId);
    }

    @Override
    public int countReferencingCrowd(long crowdId) {
        return mapper.countReferencingCrowd(crowdId);
    }

    @Override
    public TaskDefinitionEntity getByIdForUpdate(long id) {
        return mapper.selectByIdForUpdate(id);
    }

    @Override
    public List<TaskDefinitionEntity> listDueScheduled(java.time.LocalDateTime now, int limit) {
        return mapper.selectDueScheduled(now, limit);
    }

    @Override
    public int countInProgressInstances(long taskId) {
        return mapper.countInProgressInstances(taskId);
    }
}
