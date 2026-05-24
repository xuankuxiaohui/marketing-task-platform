package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.context.UserContext;
import com.marketing.task.common.EventType;
import com.marketing.task.domain.entity.MutexGroup;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.vo.TaskAdminVO;
import com.marketing.task.domain.vo.TaskClientVO;
import com.marketing.task.domain.vo.TaskStepPlatformVO;
import com.marketing.task.domain.dto.TaskSnapshotDTO;
import com.marketing.task.domain.entity.TaskDefinitionSnapshot;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.MutexGroupMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.utils.JsonUtil;
import com.marketing.task.service.cycle.CycleKeyResolver;
import com.marketing.task.service.filter.FilterEvaluator;
import com.marketing.task.service.step.StepAdvanceEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final TaskStepPlatformMapper taskStepPlatformMapper;
    private final TaskDefinitionSnapshotMapper snapshotMapper;
    private final TaskDefinitionCacheService cacheService;
    private final MutexGroupMapper mutexGroupMapper;
    private final EventTrackingService eventTrackingService;

    public List<TaskClientVO> listPublished(UserContext userContext) {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getStatus, TaskStatus.PUBLISHED.name())
                        .and(wrapper -> wrapper.isNull(Task::getStartTime).or().le(Task::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(Task::getEndTime).or().ge(Task::getEndTime, now)))
                .stream()
                .filter(task -> filterEvaluator.match(task, userContext))
                .toList();

        tasks = filterMutexConflicts(tasks, userContext);

        return tasks.stream().map(TaskClientVO::from).toList();
    }

    private List<Task> filterMutexConflicts(List<Task> tasks, UserContext userContext) {
        List<Long> mutexGroupIds = tasks.stream()
                .map(Task::getMutexGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mutexGroupIds.isEmpty()) return tasks;

        List<UserTaskInstance> activeInstances = instanceMapper.selectList(
                new LambdaQueryWrapper<UserTaskInstance>()
                        .eq(UserTaskInstance::getUserId, userContext.getUserId())
                        .in(UserTaskInstance::getStatus, InstanceStatus.PENDING.name(), InstanceStatus.IN_PROGRESS.name()));
        if (activeInstances.isEmpty()) return tasks;

        Set<Long> activeTaskIds = activeInstances.stream()
                .map(UserTaskInstance::getTaskId)
                .collect(Collectors.toSet());
        Map<Long, Task> activeTaskMap = taskMapper.selectBatchIds(activeTaskIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, MutexGroup> groupMap = mutexGroupMapper.selectBatchIds(mutexGroupIds).stream()
                .collect(Collectors.toMap(MutexGroup::getId, Function.identity()));

        Set<String> blockedKeys = new HashSet<>();
        Map<String, Long> keyOwner = new HashMap<>(); // blockedKey -> taskId that "owns" the slot

        for (UserTaskInstance inst : activeInstances) {
            Task activeTask = activeTaskMap.get(inst.getTaskId());
            if (activeTask == null || activeTask.getMutexGroupId() == null) continue;
            MutexGroup group = groupMap.get(activeTask.getMutexGroupId());
            if (group == null) continue;

            String key;
            if ("SAME_CYCLE".equals(group.getScope())) {
                key = activeTask.getMutexGroupId() + ":" + inst.getCycleKey();
            } else {
                key = activeTask.getMutexGroupId() + ":*";
            }
            blockedKeys.add(key);
            keyOwner.putIfAbsent(key, activeTask.getId());
        }

        if (blockedKeys.isEmpty()) return tasks;

        return tasks.stream().filter(task -> {
            if (task.getMutexGroupId() == null) return true;
            MutexGroup group = groupMap.get(task.getMutexGroupId());
            if (group == null) return true;

            String key;
            if ("SAME_CYCLE".equals(group.getScope())) {
                key = task.getMutexGroupId() + ":" + cycleKeyResolver.resolve(task);
            } else {
                key = task.getMutexGroupId() + ":*";
            }
            if (!blockedKeys.contains(key)) return true;
            Long ownerId = keyOwner.get(key);
            return task.getId().equals(ownerId);
        }).toList();
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
        checkMutex(task, userContext);
        instance = new UserTaskInstance();
        instance.setUserId(userContext.getUserId());
        instance.setTaskId(task.getId());
        instance.setTaskVersion(task.getVersion());
        instance.setCycleKey(cycleKey);
        instance.setStatus(InstanceStatus.PENDING.name());
        instance.setCurrentStepSeq(1);
        try {
            instanceMapper.insert(instance);
            eventTrackingService.track(EventType.INSTANCE_CREATED, task.getId(), instance.getId(), null,
                    userContext.getUserId(),
                    userContext.getPlatform() != null ? userContext.getPlatform().name() : null,
                    Map.of());
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
        if (task.getMutexGroupId() == null) {
            return;
        }
        MutexGroup mutexGroup = mutexGroupMapper.selectById(task.getMutexGroupId());
        if (mutexGroup == null) {
            return;
        }
        List<Long> mutexTaskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .select(Task::getId)
                        .eq(Task::getMutexGroupId, task.getMutexGroupId())
                        .ne(Task::getId, task.getId()))
                .stream().map(Task::getId).toList();
        if (mutexTaskIds.isEmpty()) {
            return;
        }

        // SAME_CYCLE: only check current cycle; FULL_LIFECYCLE: check all cycles
        LambdaQueryWrapper<UserTaskInstance> wrapper = new LambdaQueryWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getUserId, userContext.getUserId())
                .in(UserTaskInstance::getTaskId, mutexTaskIds)
                .in(UserTaskInstance::getStatus, InstanceStatus.PENDING.name(), InstanceStatus.IN_PROGRESS.name());
        if ("SAME_CYCLE".equals(mutexGroup.getScope())) {
            String currentCycleKey = cycleKeyResolver.resolve(task);
            wrapper.eq(UserTaskInstance::getCycleKey, currentCycleKey);
        }
        Long count = instanceMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.MUTEX_CONFLICT);
        }
    }

    public Task requireTask(Long taskId) {
        Task task = cacheService.getTask(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    public UserTaskInstance requireInstance(Long instanceId) {
        UserTaskInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) throw new BusinessException(ErrorCode.INSTANCE_NOT_FOUND);
        return instance;
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

        java.util.Map<String, Long> stepCodeToId = new java.util.LinkedHashMap<>();

        if (dto.getSteps() != null) {
            taskStepMapper.delete(new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId));
            int seq = 1;
            for (var stepVo : dto.getSteps()) {
                TaskStep step = stepVo.toEntity();
                step.setId(null);
                step.setTaskId(taskId);
                if (step.getSeq() == null) step.setSeq(seq++);
                taskStepMapper.insert(step);
                stepCodeToId.put(step.getCode(), step.getId());
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

        if (dto.getStepPlatforms() != null && !dto.getStepPlatforms().isEmpty()) {
            taskStepPlatformMapper.delete(new LambdaQueryWrapper<TaskStepPlatform>()
                    .apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId));
            for (TaskStepPlatformVO vo : dto.getStepPlatforms()) {
                if (vo.getStepCode() == null) continue;
                Long stepId = stepCodeToId.get(vo.getStepCode());
                if (stepId == null) continue;
                TaskStepPlatform entity = vo.toEntity();
                entity.setId(null);
                entity.setStepId(stepId);
                taskStepPlatformMapper.insert(entity);
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
