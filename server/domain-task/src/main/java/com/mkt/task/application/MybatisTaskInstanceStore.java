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

    @Override
    public TaskInstanceStepEntity getStep(long instanceId, String stepCode) {
        return steps.selectByInstanceAndCode(instanceId, stepCode);
    }

    @Override
    public TaskInstanceStepEntity getStepById(long id) {
        return steps.selectById(id);
    }

    @Override
    public int completeStepCas(long id, int version, LocalDateTime completedAt, Integer progressCurrent) {
        return steps.completeCas(id, version, completedAt, progressCurrent);
    }

    @Override
    public int skipStepCas(long id, int version, LocalDateTime completedAt, String skipReason) {
        return steps.skipCas(id, version, completedAt, skipReason);
    }

    @Override
    public int addProgressCas(long id, int version, int progressCurrent) {
        return steps.addProgressCas(id, version, progressCurrent);
    }

    @Override
    public int updateLastBizNo(long id, String lastBizNo) {
        return steps.updateLastBizNo(id, lastBizNo);
    }

    @Override
    public List<TaskInstanceEntity> listAdmin(
            Long taskId,
            Long userId,
            String status,
            Integer simulated,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return instances.selectAdminPage(taskId, userId, status, simulated, from, to, offset, limit);
    }

    @Override
    public long countAdmin(
            Long taskId, Long userId, String status, Integer simulated, LocalDateTime from, LocalDateTime to) {
        return instances.countAdminPage(taskId, userId, status, simulated, from, to);
    }

    @Override
    public List<TaskInstanceEntity> listDueToExpire(LocalDateTime now, int limit) {
        return instances.selectDueToExpire(now, limit);
    }

    @Override
    public int abandonCas(long id, String source, LocalDateTime abandonedAt, int costSeconds) {
        return instances.abandonCas(id, source, abandonedAt, costSeconds);
    }

    @Override
    public int expireCas(long id, LocalDateTime now, int costSeconds) {
        return instances.expireCas(id, now, costSeconds);
    }
}
