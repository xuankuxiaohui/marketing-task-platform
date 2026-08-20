package com.mkt.task.application;

import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.mapper.TaskInstanceMapper;
import com.mkt.task.mapper.TaskInstanceStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskInstanceStore implements TaskInstanceStore {

    private final TaskInstanceMapper instances;
    private final TaskInstanceStepMapper steps;

    public MybatisTaskInstanceStore(TaskInstanceMapper instances, TaskInstanceStepMapper steps) {
        this.instances = instances;
        this.steps = steps;
    }

    @Override
    public int insert(TaskInstanceEntity entity) {
        return instances.insert(entity);
    }

    @Override
    public TaskInstanceEntity getById(long id) {
        return instances.selectById(id);
    }

    @Override
    public TaskInstanceEntity getByUserTaskCycle(long userId, long taskId, String cycleKey) {
        return instances.selectByUserTaskCycle(userId, taskId, cycleKey);
    }

    @Override
    public List<TaskInstanceEntity> listByUser(long userId) {
        return instances.selectByUser(userId);
    }

    @Override
    public List<TaskInstanceEntity> listInProgressByUser(long userId) {
        return instances.selectInProgressByUser(userId);
    }

    @Override
    public List<TaskInstanceEntity> listMine(
            long userId, String status, List<Long> categoryTaskIds, long offset, int limit) {
        return instances.selectByUserAndStatus(userId, status, categoryTaskIds, offset, limit);
    }

    @Override
    public long countMine(long userId, String status, List<Long> categoryTaskIds) {
        return instances.countByUserAndStatus(userId, status, categoryTaskIds);
    }

    @Override
    public int countToday(long userId, LocalDateTime from, LocalDateTime to) {
        return instances.countToday(userId, from, to);
    }

    @Override
    public boolean existsInProgress(long userId, List<Long> taskIds, String cycleKey) {
        if (taskIds == null || taskIds.isEmpty()) {
            return false;
        }
        return instances.existsInProgress(userId, taskIds, cycleKey) > 0;
    }

    @Override
    public long countInProgress(long userId) {
        return instances.countInProgress(userId);
    }

    @Override
    public long countHistory(long userId) {
        return instances.countHistory(userId);
    }

    @Override
    public int completeInstance(long id, LocalDateTime completedAt, int costSeconds) {
        return instances.completeInstance(id, completedAt, costSeconds);
    }

    @Override
    public int insertStep(TaskInstanceStepEntity entity) {
        return steps.insert(entity);
    }

    @Override
    public List<TaskInstanceStepEntity> listSteps(long instanceId) {
        return steps.selectByInstanceId(instanceId);
    }

    @Override
    public int activateStep(long id, LocalDateTime activatedAt) {
        return steps.activate(id, activatedAt);
    }

    @Override
    public int completeStep(long id, LocalDateTime completedAt) {
        return steps.complete(id, completedAt);
    }
}
