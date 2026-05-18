package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.cycle.CycleKeyResolver;
import com.marketing.task.service.filter.FilterEvaluator;
import com.marketing.task.service.step.StepAdvanceEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;
    private final UserTaskInstanceMapper instanceMapper;
    private final CycleKeyResolver cycleKeyResolver;
    private final FilterEvaluator filterEvaluator;
    private final StepAdvanceEngine stepAdvanceEngine;

    public List<Task> listPublished(UserContext userContext) {
        LocalDateTime now = LocalDateTime.now();
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, TaskStatus.PUBLISHED.name())
                        .and(wrapper -> wrapper.isNull(Task::getStartTime).or().le(Task::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(Task::getEndTime).or().ge(Task::getEndTime, now)))
                .stream()
                .filter(task -> filterEvaluator.match(task, userContext))
                .toList();
    }

    @Transactional
    public UserTaskInstance getOrCreateInstance(Task task, UserContext userContext) {
        String cycleKey = cycleKeyResolver.resolve(task);
        UserTaskInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getUserId, userContext.getUserId())
                .eq(UserTaskInstance::getTaskId, task.getId())
                .eq(UserTaskInstance::getCycleKey, cycleKey));
        if (instance != null) {
            return instance;
        }
        instance = new UserTaskInstance();
        instance.setUserId(userContext.getUserId());
        instance.setTaskId(task.getId());
        instance.setTaskVersion(task.getVersion());
        instance.setCycleKey(cycleKey);
        instance.setStatus(InstanceStatus.PENDING.name());
        instance.setCurrentStepSeq(1);
        try {
            instanceMapper.insert(instance);
        } catch (DuplicateKeyException ex) {
            log.warn("Concurrent instance creation detected, re-querying: userId={}, taskId={}, cycleKey={}",
                    userContext.getUserId(), task.getId(), cycleKey);
            return instanceMapper.selectOne(new LambdaQueryWrapper<UserTaskInstance>()
                    .eq(UserTaskInstance::getUserId, userContext.getUserId())
                    .eq(UserTaskInstance::getTaskId, task.getId())
                    .eq(UserTaskInstance::getCycleKey, cycleKey));
        }
        return stepAdvanceEngine.enter(instance);
    }

    public Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    @Transactional
    public void publish(Long taskId) {
        Task task = requireTask(taskId);
        task.setStatus(TaskStatus.PUBLISHED.name());
        task.setVersion(task.getVersion() == null ? 1 : task.getVersion() + 1);
        taskMapper.updateById(task);
    }

    @Transactional
    public void offline(Long taskId) {
        Task task = requireTask(taskId);
        task.setStatus(TaskStatus.OFFLINE.name());
        taskMapper.updateById(task);
    }
}
