package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.context.UserContext;
import com.marketing.common.EventType;
import com.marketing.task.domain.entity.MutexGroup;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.domain.dto.BatchTaskResult;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.entity.TaskStepTransition;
import com.marketing.task.domain.vo.TaskAdminVO;
import com.marketing.task.domain.vo.TaskClientVO;
import com.marketing.task.domain.vo.TaskStepPlatformVO;
import com.marketing.task.domain.vo.TaskStepTransitionVO;
import com.marketing.task.domain.dto.TaskSnapshotDTO;
import com.marketing.task.domain.entity.TaskDefinitionSnapshot;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.MutexGroupMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.mapper.TaskStepTransitionMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.EventTrackingService;
import com.marketing.utils.JsonUtil;
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
    private final TaskStepTransitionMapper transitionMapper;
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

        Set<Long> activeTaskIds = activeInstances.stream()
                .map(UserTaskInstance::getTaskId)
                .collect(Collectors.toSet());
        if (activeTaskIds.isEmpty()) return tasks;

        Map<Long, Task> activeTaskMap = taskMapper.selectBatchIds(activeTaskIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, MutexGroup> groupMap = mutexGroupMapper.selectBatchIds(mutexGroupIds).stream()
                .collect(Collectors.toMap(MutexGroup::getId, Function.identity()));

        Set<String> blockedKeys = new HashSet<>();
        Map<String, Long> keyOwner = new HashMap<>();

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

        // Cross-cycle mutex: also collect COMPLETED/REWARDED instances for mutex groups with crossCycle=true
        Set<Long> crossCycleGroupIds = groupMap.values().stream()
                .filter(g -> Boolean.TRUE.equals(g.getCrossCycle()))
                .map(MutexGroup::getId)
                .collect(Collectors.toSet());
        if (!crossCycleGroupIds.isEmpty()) {
            Set<Long> crossCycleTaskIds = activeTaskMap.keySet().stream()
                    .filter(tid -> {
                        Task t = activeTaskMap.get(tid);
                        return t != null && t.getMutexGroupId() != null
                                && crossCycleGroupIds.contains(t.getMutexGroupId());
                    })
                    .collect(Collectors.toSet());
            // extend to include all tasks in those mutex groups
            List<Long> allCrossMutexTaskIds = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                            .select(Task::getId)
                            .in(Task::getMutexGroupId, crossCycleGroupIds))
                    .stream().map(Task::getId).toList();

            if (!allCrossMutexTaskIds.isEmpty()) {
                List<UserTaskInstance> completedInstances = instanceMapper.selectList(
                        new LambdaQueryWrapper<UserTaskInstance>()
                                .eq(UserTaskInstance::getUserId, userContext.getUserId())
                                .in(UserTaskInstance::getTaskId, allCrossMutexTaskIds)
                                .in(UserTaskInstance::getStatus, InstanceStatus.COMPLETED.name(),
                                        InstanceStatus.REWARDED.name()));
                for (UserTaskInstance inst : completedInstances) {
                    Task completedTask = taskMapper.selectById(inst.getTaskId());
                    if (completedTask == null || completedTask.getMutexGroupId() == null) continue;
                    MutexGroup group = groupMap.get(completedTask.getMutexGroupId());
                    if (group == null || !Boolean.TRUE.equals(group.getCrossCycle())) continue;
                    String key = completedTask.getMutexGroupId() + ":*";
                    blockedKeys.add(key);
                    keyOwner.putIfAbsent(key, completedTask.getId());
                }
            }
        }

        if (activeInstances.isEmpty() && blockedKeys.isEmpty()) return tasks;

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

        // Cross-cycle mutex: block if user has COMPLETED/REWARDED instance in ANY cycle
        if (Boolean.TRUE.equals(mutexGroup.getCrossCycle())) {
            LambdaQueryWrapper<UserTaskInstance> crossWrapper = new LambdaQueryWrapper<UserTaskInstance>()
                    .eq(UserTaskInstance::getUserId, userContext.getUserId())
                    .in(UserTaskInstance::getTaskId, mutexTaskIds)
                    .in(UserTaskInstance::getStatus, InstanceStatus.COMPLETED.name(), InstanceStatus.REWARDED.name());
            Long crossCount = instanceMapper.selectCount(crossWrapper);
            if (crossCount > 0) {
                throw new BusinessException(ErrorCode.MUTEX_CONFLICT);
            }
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

        // Process step transitions
        if (dto.getTransitions() != null) {
            transitionMapper.deleteByTaskId(taskId);
            // Build code-to-entity map for target resolution and validation
            java.util.Map<String, TaskStep> codeToStep = new java.util.LinkedHashMap<>();
            for (TaskStep step : taskStepMapper.selectList(
                    new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId))) {
                codeToStep.put(step.getCode(), step);
            }
            for (TaskStepTransitionVO vo : dto.getTransitions()) {
                if (vo.getStepCode() == null || vo.getTargetStepCode() == null) continue;
                TaskStep sourceStep = codeToStep.get(vo.getStepCode());
                TaskStep targetStep = codeToStep.get(vo.getTargetStepCode());
                if (sourceStep == null || targetStep == null) {
                    log.warn("Skipping transition: source or target step not found: {} -> {}",
                            vo.getStepCode(), vo.getTargetStepCode());
                    continue;
                }
                // Validate: no self-referencing
                if (sourceStep.getId().equals(targetStep.getId())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "分支不能指向自身: " + vo.getStepCode());
                }
                // Validate: target seq > source seq (no backward jumps)
                if (targetStep.getSeq() <= sourceStep.getSeq()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "分支目标步骤序号必须大于源步骤: " + vo.getStepCode() + " -> " + vo.getTargetStepCode());
                }
                TaskStepTransition entity = vo.toEntity();
                entity.setId(null);
                entity.setStepId(sourceStep.getId());
                entity.setTargetStepId(targetStep.getId());
                transitionMapper.insert(entity);
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
List<TaskStepPlatform> stepPlatforms = taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId));
        List<TaskStepTransition> transitions = transitionMapper.selectByTaskId(taskId);

        TaskSnapshotDTO snapshot = new TaskSnapshotDTO(task, steps, filters, platforms, stepPlatforms, transitions);
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

    @Transactional
    public void deleteTask(Long taskId) {
        Task task = requireTask(taskId);
        if (TaskStatus.PUBLISHED.name().equals(task.getStatus())
                || TaskStatus.SCHEDULED.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "已发布或定时发布的任务不能删除，请先下线");
        }
        task.setDeleted(1);
        taskMapper.deleteById(task);
        cacheService.evict(taskId);
    }

    @Transactional
    public void restoreTask(Long taskId) {
        Task task = taskMapper.selectDeletedById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在或未被删除");
        }
        task.setDeleted(0);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        cacheService.evict(taskId);
        log.info("恢复任务: id={}", taskId);
    }

    public BatchTaskResult batchPublish(List<Long> taskIds) {
        if (taskIds.size() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批量操作最多支持50个任务");
        }
        List<Long> success = new ArrayList<>();
        List<BatchTaskResult.FailedItem> failed = new ArrayList<>();
        for (Long taskId : taskIds) {
            try {
                publish(taskId);
                success.add(taskId);
            } catch (Exception e) {
                failed.add(new BatchTaskResult.FailedItem(taskId, e.getMessage()));
            }
        }
        return new BatchTaskResult(success, failed);
    }

    public BatchTaskResult batchOffline(List<Long> taskIds) {
        if (taskIds.size() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批量操作最多支持50个任务");
        }
        List<Long> success = new ArrayList<>();
        List<BatchTaskResult.FailedItem> failed = new ArrayList<>();
        for (Long taskId : taskIds) {
            try {
                offline(taskId);
                success.add(taskId);
            } catch (Exception e) {
                failed.add(new BatchTaskResult.FailedItem(taskId, e.getMessage()));
            }
        }
        return new BatchTaskResult(success, failed);
    }

    @Transactional
    public void schedulePublish(Long taskId, LocalDateTime publishAt) {
        if (publishAt == null || !publishAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "定时发布时间必须在当前时间之后");
        }
        Task task = requireTask(taskId);
        if (!TaskStatus.DRAFT.name().equals(task.getStatus()) && !TaskStatus.OFFLINE.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "只有草稿或已下线的任务可以设置定时发布");
        }
        task.setStatus(TaskStatus.SCHEDULED.name());
        task.setScheduledPublishAt(publishAt);
        taskMapper.updateById(task);
        cacheService.evict(taskId);
    }

    @Transactional
    public void cancelScheduledPublish(Long taskId) {
        Task task = requireTask(taskId);
        if (!TaskStatus.SCHEDULED.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "任务不是定时发布状态");
        }
        task.setStatus(TaskStatus.DRAFT.name());
        task.setScheduledPublishAt(null);
        taskMapper.updateById(task);
        cacheService.evict(taskId);
    }

    @Transactional
    public Long copyTask(Long sourceTaskId, String customName, String customCode) {
        Task source = requireTask(sourceTaskId);

        Task copy = new Task();
        copy.setCode(customCode != null && !customCode.isBlank() ? customCode : source.getCode() + "_copy_" + System.currentTimeMillis() / 1000);
        copy.setName(customName != null && !customName.isBlank() ? customName : source.getName() + " (副本)");
        copy.setDescription(source.getDescription());
        copy.setPeriodType(source.getPeriodType());
        copy.setCronExpr(source.getCronExpr());
        copy.setSpecialCycleKey(source.getSpecialCycleKey());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setStatus(TaskStatus.DRAFT.name());
        copy.setVersion(0);
        copy.setMutexGroupId(source.getMutexGroupId());
        copy.setGrayType(source.getGrayType());
        copy.setGrayConfig(source.getGrayConfig());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        taskMapper.insert(copy);
        Long newTaskId = copy.getId();

        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, sourceTaskId)
                        .orderByAsc(TaskStep::getSeq));
        for (TaskStep step : steps) {
            step.setId(null);
            step.setTaskId(newTaskId);
            taskStepMapper.insert(step);
        }

        List<TaskFilter> filters = taskFilterMapper.selectList(
                new LambdaQueryWrapper<TaskFilter>()
                        .eq(TaskFilter::getTaskId, sourceTaskId)
                        .orderByAsc(TaskFilter::getSeq));
        for (TaskFilter filter : filters) {
            filter.setId(null);
            filter.setTaskId(newTaskId);
            taskFilterMapper.insert(filter);
        }

        List<TaskPlatform> platforms = taskPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskPlatform>()
                        .eq(TaskPlatform::getTaskId, sourceTaskId));
        for (TaskPlatform platform : platforms) {
            platform.setId(null);
            platform.setTaskId(newTaskId);
            taskPlatformMapper.insert(platform);
        }

        // Copy transitions: map old step IDs to new step IDs
        List<TaskStep> newSteps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, newTaskId));
        java.util.Map<String, Long> newStepCodeToId = new java.util.LinkedHashMap<>();
        for (TaskStep ns : newSteps) {
            newStepCodeToId.put(ns.getCode(), ns.getId());
        }
        // Build old code→new id map from the copied steps
        java.util.Map<String, Long> oldCodeToNewId = new java.util.LinkedHashMap<>();
        java.util.Map<Long, String> oldIdToCode = new java.util.LinkedHashMap<>();
        for (TaskStep oldStep : steps) {
            oldIdToCode.put(oldStep.getId(), oldStep.getCode());
        }
        for (TaskStep ns : newSteps) {
            oldCodeToNewId.put(ns.getCode(), ns.getId());
        }
        List<TaskStepTransition> transitions = transitionMapper.selectByTaskId(sourceTaskId);
        for (TaskStepTransition t : transitions) {
            String sourceCode = oldIdToCode.get(t.getStepId());
            String targetCode = oldIdToCode.get(t.getTargetStepId());
            if (sourceCode == null || targetCode == null) continue;
            Long newStepId = oldCodeToNewId.get(sourceCode);
            Long newTargetStepId = oldCodeToNewId.get(targetCode);
            if (newStepId == null || newTargetStepId == null) continue;
            t.setId(null);
            t.setStepId(newStepId);
            t.setTargetStepId(newTargetStepId);
            transitionMapper.insert(t);
        }

        cacheService.evict(newTaskId);
        return newTaskId;
    }
}
