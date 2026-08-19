package com.mkt.task.application;

import com.mkt.task.entity.TaskDefinitionEntity;
import java.util.List;

public interface TaskDefinitionStore {

    TaskDefinitionEntity getById(long id);

    TaskDefinitionEntity getByCode(String code);

    int insert(TaskDefinitionEntity entity);

    int update(TaskDefinitionEntity entity);

    long countByQuery(String code, String name, String status, String category);

    List<TaskDefinitionEntity> listByQuery(
            String code, String name, String status, String category, long offset, int limit);

    List<String> cycleTypesInMutexGroup(long mutexGroupId, Long excludeTaskId);

    int countByMutexGroup(long mutexGroupId);

    int countReferencingCrowd(long crowdId);
}
