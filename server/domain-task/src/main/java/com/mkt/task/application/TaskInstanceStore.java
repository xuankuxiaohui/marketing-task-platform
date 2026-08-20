package com.mkt.task.application;

import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskInstanceStore {

    int insert(TaskInstanceEntity entity);

    TaskInstanceEntity getById(long id);

    TaskInstanceEntity getByUserTaskCycle(long userId, long taskId, String cycleKey);

    List<TaskInstanceEntity> listByUser(long userId);

    List<TaskInstanceEntity> listInProgressByUser(long userId);

    List<TaskInstanceEntity> listMine(
            long userId, String status, List<Long> categoryTaskIds, long offset, int limit);

    long countMine(long userId, String status, List<Long> categoryTaskIds);

    int countToday(long userId, LocalDateTime from, LocalDateTime to);

    boolean existsInProgress(long userId, List<Long> taskIds, String cycleKey);

    long countInProgress(long userId);

    long countHistory(long userId);

    int completeInstance(long id, LocalDateTime completedAt, int costSeconds);

    int insertStep(TaskInstanceStepEntity entity);

    List<TaskInstanceStepEntity> listSteps(long instanceId);

    int activateStep(long id, LocalDateTime activatedAt);

    int completeStep(long id, LocalDateTime completedAt);
}
