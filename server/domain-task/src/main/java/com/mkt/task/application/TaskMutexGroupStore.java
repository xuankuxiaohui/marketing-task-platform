package com.mkt.task.application;

import com.mkt.task.entity.TaskMutexGroupEntity;
import java.util.List;

public interface TaskMutexGroupStore {

    TaskMutexGroupEntity getById(long id);

    TaskMutexGroupEntity getByCode(String code);

    int insert(TaskMutexGroupEntity entity);

    int update(TaskMutexGroupEntity entity);

    int deleteById(long id);

    long countAll();

    List<TaskMutexGroupEntity> list(long offset, int limit);
}
