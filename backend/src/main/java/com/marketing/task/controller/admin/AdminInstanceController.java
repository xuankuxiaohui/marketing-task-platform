package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.common.Result;
import com.marketing.system.domain.entity.EventLog;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.entity.UserTaskStepProgress;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.system.mapper.EventLogMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.mapper.UserTaskStepProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Admin - Instances", description = "用户实例管理")
@RestController
@RequestMapping("/api/admin/instance")
@RequiredArgsConstructor
public class AdminInstanceController {
    private final UserTaskInstanceMapper instanceMapper;
    private final UserTaskStepProgressMapper stepProgressMapper;
    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final EventLogMapper eventLogMapper;

    @Operation(summary = "分页查询用户实例")
    @GetMapping
    public Result<IPage<UserTaskInstanceVO>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) Long taskId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) LocalDate startDate,
                                                   @RequestParam(required = false) LocalDate endDate) {
        LambdaQueryWrapper<UserTaskInstance> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(userId)) {
            wrapper.like(UserTaskInstance::getUserId, userId);
        }
        if (taskId != null) {
            wrapper.eq(UserTaskInstance::getTaskId, taskId);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(UserTaskInstance::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(UserTaskInstance::getCreatedAt, LocalDateTime.of(startDate, LocalTime.MIN));
        }
        if (endDate != null) {
            wrapper.le(UserTaskInstance::getCreatedAt, LocalDateTime.of(endDate, LocalTime.MAX));
        }

        wrapper.orderByDesc(UserTaskInstance::getId);

        IPage<UserTaskInstance> result = instanceMapper.selectPage(Page.of(page, size), wrapper);

        // Batch-load task names for enrichment
        Map<Long, String> taskNameMap = loadTaskNameMap(result.getRecords());

        IPage<UserTaskInstanceVO> voPage = result.convert(entity -> {
            UserTaskInstanceVO vo = UserTaskInstanceVO.from(entity);
            vo.setTaskName(taskNameMap.get(entity.getTaskId()));
            return vo;
        });

        return Result.ok(voPage);
    }

    @Operation(summary = "获取实例详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        UserTaskInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(ErrorCode.INSTANCE_NOT_FOUND);
        }

        // Load step progress records
        List<UserTaskStepProgress> stepProgresses = stepProgressMapper.selectList(
                new LambdaQueryWrapper<UserTaskStepProgress>()
                        .eq(UserTaskStepProgress::getInstanceId, id)
                        .orderByAsc(UserTaskStepProgress::getId));

        // Load task step definitions
        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, instance.getTaskId())
                        .orderByAsc(TaskStep::getSeq));

        // Load task name
        Task task = taskMapper.selectById(instance.getTaskId());

        // Build step detail list: merge step definitions with progress records
        Map<Long, UserTaskStepProgress> progressMap = stepProgresses.stream()
                .collect(Collectors.toMap(UserTaskStepProgress::getStepId, p -> p, (a, b) -> a));

        List<Map<String, Object>> stepDetails = new ArrayList<>();
        for (TaskStep step : steps) {
            UserTaskStepProgress progress = progressMap.get(step.getId());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("stepId", step.getId());
            detail.put("stepSeq", step.getSeq());
            detail.put("stepName", step.getName());
            detail.put("stepType", step.getType());
            detail.put("stepDescription", step.getDescription());
            detail.put("targetValue", step.getTargetValue());
            detail.put("status", progress != null ? progress.getStatus() : "PENDING");
            detail.put("progressValue", progress != null ? progress.getProgressValue() : null);
            detail.put("completeTime", progress != null ? progress.getCompleteTime() : null);
            stepDetails.add(detail);
        }

        // Enrich instance VO
        UserTaskInstanceVO instanceVO = UserTaskInstanceVO.from(instance);
        instanceVO.setTaskName(task != null ? task.getName() : null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instance", instanceVO);
        data.put("steps", stepDetails);
        data.put("totalSteps", steps.size());
        return Result.ok(data);
    }

    @Operation(summary = "获取实例事件日志")
    @GetMapping("/{id}/events")
    public Result<List<EventLog>> events(@PathVariable Long id) {
        List<EventLog> events = eventLogMapper.selectList(
                new LambdaQueryWrapper<EventLog>()
                        .eq(EventLog::getInstanceId, id)
                        .orderByAsc(EventLog::getCreatedAt)
                        .orderByAsc(EventLog::getId)
                        .last("LIMIT 100"));
        return Result.ok(events);
    }

    /**
     * Batch-load task names from task table for the given instances.
     */
    private Map<Long, String> loadTaskNameMap(List<UserTaskInstance> instances) {
        Set<Long> taskIds = instances.stream()
                .map(UserTaskInstance::getTaskId)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, Task::getName, (a, b) -> a));
    }
}
