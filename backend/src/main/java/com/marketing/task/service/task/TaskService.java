package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.vo.TaskAdminVO;
import com.marketing.task.domain.vo.TaskClientVO;
import com.marketing.task.domain.dto.TaskSnapshotDTO;
import com.marketing.task.domain.entity.TaskDefinitionSnapshot;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.utils.JsonUtil;
import com.marketing.task.service.cycle.CycleKeyResolver;
import com.marketing.task.service.filter.FilterEvaluator;
import com.marketing.task.service.step.StepAdvanceEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final TaskStepMapper taskStepMapper;
    private final TaskFilterMapper taskFilterMapper;
    private final TaskPlatformMapper taskPlatformMapper;
    private final TaskDefinitionSnapshotMapper snapshotMapper;
    private final TaskDefinitionCacheService cacheService;

    public List<TaskClientVO> listPublished(UserContext userContext) {
        LocalDateTime now = LocalDateTime.now();
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, TaskStatus.PUBLISHED.name())
                        .and(wrapper -> wrapper.isNull(Task::getStartTime).or().le(Task::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(Task::getEndTime).or().ge(Task::getEndTime, now)))
                .stream()
                .filter(task -> filterEvaluator.match(task, userContext))
                .map(TaskClientVO::from)
                .toList();
    }

    @Transactional
    public UserTaskInstance getOrCreateInstance(Task task, UserContext userContext) {
        checkMutex(task, userContext);
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

    private void checkMutex(Task task, UserContext userContext) {
        if (!StringUtils.hasText(task.getMutexGroupKey())) {
            return;
        }
        List<Long> mutexTaskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .select(Task::getId)
                        .eq(Task::getMutexGroupKey, task.getMutexGroupKey())
                        .ne(Task::getId, task.getId()))
                .stream().map(Task::getId).toList();
        if (mutexTaskIds.isEmpty()) {
            return;
        }
        Long count = instanceMapper.selectCount(new LambdaQueryWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getUserId, userContext.getUserId())
                .in(UserTaskInstance::getTaskId, mutexTaskIds)
                .in(UserTaskInstance::getStatus, InstanceStatus.PENDING.name(), InstanceStatus.IN_PROGRESS.name()));
        if (count > 0) {
            throw new BusinessException("您有一个互斥任务正在进行中，请先完成它");
        }
    }

    public Task requireTask(Long taskId) {
        Task task = cacheService.getTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    @Transactional
    public TaskAdminVO saveAggregate(TaskAggregateDTO dto) {
        Task task = dto.getTask().toEntity();
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            taskMapper.updateById(task);
        }
        Long taskId = task.getId();

        if (dto.getSteps() != null) {
            taskStepMapper.delete(new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId));
            int seq = 1;
            for (var stepVo : dto.getSteps()) {
                TaskStep step = stepVo.toEntity();
                step.setId(null);
                step.setTaskId(taskId);
                if (step.getSeq() == null) step.setSeq(seq++);
                taskStepMapper.insert(step);
            }
        }

        if (dto.getFilters() != null) {
            taskFilterMapper.delete(new LambdaQueryWrapper<TaskFilter>().eq(TaskFilter::getTaskId, taskId));
            int seq = 1;
            for (var filterVo : dto.getFilters()) {
                TaskFilter filter = filterVo.toEntity();
                filter.setId(null);
                filter.setTaskId(taskId);
                if (filter.getSeq() == null) filter.setSeq(seq++);
                taskFilterMapper.insert(filter);
            }
        }

        if (dto.getPlatforms() != null) {
            taskPlatformMapper.delete(new LambdaQueryWrapper<TaskPlatform>().eq(TaskPlatform::getTaskId, taskId));
            for (var platformVo : dto.getPlatforms()) {
                TaskPlatform platform = platformVo.toEntity();
                platform.setId(null);
                platform.setTaskId(taskId);
                taskPlatformMapper.insert(platform);
            }
        }

        cacheService.evict(taskId);
        return TaskAdminVO.from(task);
    }

    @Transactional
    public void publish(Long taskId) {
        Task task = requireTask(taskId);
        task.setStatus(TaskStatus.PUBLISHED.name());
        task.setVersion(task.getVersion() == null ? 1 : task.getVersion() + 1);
        taskMapper.updateById(task);

        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, taskId)
                        .orderByAsc(TaskStep::getSeq));
        List<TaskFilter> filters = taskFilterMapper.selectList(
                new LambdaQueryWrapper<TaskFilter>()
                        .eq(TaskFilter::getTaskId, taskId)
                        .orderByAsc(TaskFilter::getSeq));
        List<TaskPlatform> platforms = taskPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskPlatform>()
                        .eq(TaskPlatform::getTaskId, taskId));

        TaskSnapshotDTO snapshot = new TaskSnapshotDTO(task, steps, filters, platforms);
        TaskDefinitionSnapshot entity = new TaskDefinitionSnapshot();
        entity.setTaskId(taskId);
        entity.setVersion(task.getVersion());
        entity.setSnapshotJson(JsonUtil.objToJson(snapshot));
        snapshotMapper.insert(entity);

        cacheService.evict(taskId);
    }

    @Transactional
    public void offline(Long taskId) {
        Task task = requireTask(taskId);
        task.setStatus(TaskStatus.OFFLINE.name());
        taskMapper.updateById(task);
        cacheService.evict(taskId);
    }
}
