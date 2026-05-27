package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.domain.dto.BatchTaskRequest;
import com.marketing.task.domain.dto.BatchTaskResult;
import com.marketing.task.domain.dto.SchedulePublishRequest;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.dto.TaskCopyRequest;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskDefinitionSnapshot;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepTransition;
import com.marketing.task.domain.vo.TaskAdminVO;
import com.marketing.task.domain.vo.TaskStepTransitionVO;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.vo.TaskVersionVO;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepTransitionMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.OperationLogService;
import com.marketing.task.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/task")
@RequiredArgsConstructor
public class AdminTaskController {
    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final UserTaskInstanceMapper instanceMapper;
    private final TaskDefinitionSnapshotMapper snapshotMapper;
    private final TaskStepTransitionMapper transitionMapper;
    private final TaskService taskService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<IPage<TaskAdminVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String periodType) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(Task::getStatus, status);
        }
        if (periodType != null && !periodType.isBlank()) {
            wrapper.eq(Task::getPeriodType, periodType);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Task::getCode, keyword).or().like(Task::getName, keyword));
        }
        wrapper.orderByDesc(Task::getId);

        IPage<Task> taskPage = taskMapper.selectPage(Page.of(page, size), wrapper);
        List<TaskAdminVO> vos = taskPage.getRecords().stream()
                .map(TaskAdminVO::from)
                .collect(Collectors.toList());

        // batch enrich step counts and instance counts
        if (!vos.isEmpty()) {
            Set<Long> taskIds = vos.stream().map(TaskAdminVO::getId).collect(Collectors.toSet());
            Map<Long, Integer> stepCounts = buildCountMap(taskStepMapper.countByTaskIds(taskIds));
            Map<Long, Integer> instanceCounts = buildCountMap(instanceMapper.countByTaskIds(taskIds));
            for (TaskAdminVO vo : vos) {
                vo.setStepCount(stepCounts.getOrDefault(vo.getId(), 0));
                vo.setInstanceCount(instanceCounts.getOrDefault(vo.getId(), 0));
            }
        }

        IPage<TaskAdminVO> voPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        voPage.setRecords(vos);
        return Result.ok(voPage);
    }

    private static Map<Long, Integer> buildCountMap(List<Map<String, Object>> rows) {
        Map<Long, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long taskId = ((Number) row.get("task_id")).longValue();
            Integer cnt = ((Number) row.get("cnt")).intValue();
            map.put(taskId, cnt);
        }
        return map;
    }

    @PostMapping
    public Result<TaskAdminVO> save(@Valid @RequestBody TaskAggregateDTO dto) {
        TaskAdminVO vo = taskService.saveAggregate(dto);
        String operatorId = UserContextHolder.get().getUserId();
        String opType = dto.getTask().getId() != null ? "UPDATE" : "CREATE";
        operationLogService.record(operatorId, opType, "TASK", vo.getId(), vo.getName(), null);
        return Result.ok(vo);
    }

    @GetMapping("/{id}")
    public Result<TaskAdminVO> getById(@PathVariable Long id) {
        return Result.ok(TaskAdminVO.from(taskService.requireTask(id)));
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        taskService.publish(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "PUBLISH", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        taskService.offline(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "OFFLINE", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @PostMapping("/batch-publish")
    public Result<BatchTaskResult> batchPublish(@Valid @RequestBody BatchTaskRequest request) {
        BatchTaskResult result = taskService.batchPublish(request.getTaskIds());
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "BATCH_PUBLISH", "TASK", null, "批量发布 " + result.getSuccess().size() + " 个任务", null);
        return Result.ok(result);
    }

    @PostMapping("/batch-offline")
    public Result<BatchTaskResult> batchOffline(@Valid @RequestBody BatchTaskRequest request) {
        BatchTaskResult result = taskService.batchOffline(request.getTaskIds());
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "BATCH_OFFLINE", "TASK", null, "批量下线 " + result.getSuccess().size() + " 个任务", null);
        return Result.ok(result);
    }

    @PostMapping("/{id}/schedule-publish")
    public Result<Void> schedulePublish(@PathVariable Long id, @RequestBody SchedulePublishRequest request) {
        taskService.schedulePublish(id, request.getPublishAt());
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "SCHEDULE_PUBLISH", "TASK", id, task != null ? task.getName() : "任务#" + id, "定时发布: " + request.getPublishAt());
        return Result.ok(null);
    }

    @PostMapping("/{id}/cancel-schedule")
    public Result<Void> cancelSchedule(@PathVariable Long id) {
        taskService.cancelScheduledPublish(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "CANCEL_SCHEDULE", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id, @RequestBody(required = false) TaskCopyRequest request) {
        String customName = request != null ? request.getName() : null;
        String customCode = request != null ? request.getCode() : null;
        Long newTaskId = taskService.copyTask(id, customName, customCode);
        Task newTask = taskMapper.selectById(newTaskId);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "CREATE", "TASK", newTaskId, newTask != null ? newTask.getName() : "任务#" + newTaskId, "从任务#" + id + "复制");
        return Result.ok(newTaskId);
    }

    @GetMapping("/{id}/versions")
    public Result<List<TaskVersionVO>> versions(@PathVariable Long id) {
        List<TaskDefinitionSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<TaskDefinitionSnapshot>()
                        .eq(TaskDefinitionSnapshot::getTaskId, id)
                        .orderByDesc(TaskDefinitionSnapshot::getVersion));
        List<TaskVersionVO> vos = snapshots.stream()
                .map(TaskVersionVO::from)
                .collect(Collectors.toList());
        return Result.ok(vos);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public Result<TaskDefinitionSnapshot> versionDetail(@PathVariable Long id, @PathVariable Long versionId) {
        TaskDefinitionSnapshot snapshot = snapshotMapper.selectById(versionId);
        if (snapshot == null || !snapshot.getTaskId().equals(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本快照不存在");
        }
        return Result.ok(snapshot);
    }

    @GetMapping("/{taskId}/transitions")
    public Result<List<TaskStepTransitionVO>> transitions(@PathVariable Long taskId) {
        List<TaskStepTransition> transitions = transitionMapper.selectByTaskId(taskId);
        if (transitions.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Map<Long, String> idToCode = taskStepMapper.selectList(
                        new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId))
                .stream()
                .collect(Collectors.toMap(TaskStep::getId, TaskStep::getCode));
        List<TaskStepTransitionVO> vos = transitions.stream()
                .map(t -> {
                    TaskStepTransitionVO vo = TaskStepTransitionVO.from(t);
                    vo.setStepCode(idToCode.get(t.getStepId()));
                    vo.setTargetStepCode(idToCode.get(t.getTargetStepId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return Result.ok(vos);
    }
}
