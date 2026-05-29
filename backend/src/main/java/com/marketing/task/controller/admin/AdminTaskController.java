package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.common.Result;
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
import com.marketing.context.UserContextHolder;
import com.marketing.task.domain.vo.TaskVersionVO;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepTransitionMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.system.service.OperationLogService;
import com.marketing.task.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Admin - Tasks", description = "任务管理")
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

    @Operation(summary = "分页查询任务")
    @GetMapping
    public Result<IPage<TaskAdminVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String periodType) {

        // Handle "DELETED" status: query soft-deleted records via custom mapper
        if ("DELETED".equals(status)) {
            long offset = (page - 1) * size;
            List<Task> deletedTasks = taskMapper.selectDeletedPage(offset, size, null, keyword, periodType);
            long total = taskMapper.countDeleted(null, keyword, periodType);
            List<TaskAdminVO> vos = deletedTasks.stream().map(TaskAdminVO::from).collect(Collectors.toList());
            enrichCounts(vos);
            IPage<TaskAdminVO> voPage = new Page<>(page, size, total);
            voPage.setRecords(vos);
            return Result.ok(voPage);
        }

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

        enrichCounts(vos);

        IPage<TaskAdminVO> voPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        voPage.setRecords(vos);
        return Result.ok(voPage);
    }

    private void enrichCounts(List<TaskAdminVO> vos) {
        if (!vos.isEmpty()) {
            Set<Long> taskIds = vos.stream().map(TaskAdminVO::getId).collect(Collectors.toSet());
            Map<Long, Integer> stepCounts = buildCountMap(taskStepMapper.countByTaskIds(taskIds));
            Map<Long, Integer> instanceCounts = buildCountMap(instanceMapper.countByTaskIds(taskIds));
            for (TaskAdminVO vo : vos) {
                vo.setStepCount(stepCounts.getOrDefault(vo.getId(), 0));
                vo.setInstanceCount(instanceCounts.getOrDefault(vo.getId(), 0));
            }
        }
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

    @Operation(summary = "创建或更新任务")
    @PostMapping
    public Result<TaskAdminVO> save(@Valid @RequestBody TaskAggregateDTO dto) {
        TaskAdminVO vo = taskService.saveAggregate(dto);
        String operatorId = UserContextHolder.get().getUserId();
        String opType = dto.getTask().getId() != null ? "UPDATE" : "CREATE";
        operationLogService.record(operatorId, opType, "TASK", vo.getId(), vo.getName(), null);
        return Result.ok(vo);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public Result<TaskAdminVO> getById(@PathVariable Long id) {
        return Result.ok(TaskAdminVO.from(taskService.requireTask(id)));
    }

    @Operation(summary = "发布任务")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        taskService.publish(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "PUBLISH", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @Operation(summary = "下线任务")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        taskService.offline(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "OFFLINE", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Task task = taskMapper.selectById(id);
        String taskName = task != null ? task.getName() : "任务#" + id;
        taskService.deleteTask(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "DELETE", "TASK", id, taskName, null);
        return Result.ok(null);
    }

    @Operation(summary = "恢复已删除任务")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        taskService.restoreTask(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "RESTORE", "TASK", id,
                task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @Operation(summary = "批量发布")
    @PostMapping("/batch-publish")
    public Result<BatchTaskResult> batchPublish(@Valid @RequestBody BatchTaskRequest request) {
        BatchTaskResult result = taskService.batchPublish(request.getTaskIds());
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "BATCH_PUBLISH", "TASK", null, "批量发布 " + result.getSuccess().size() + " 个任务", null);
        return Result.ok(result);
    }

    @Operation(summary = "批量下线")
    @PostMapping("/batch-offline")
    public Result<BatchTaskResult> batchOffline(@Valid @RequestBody BatchTaskRequest request) {
        BatchTaskResult result = taskService.batchOffline(request.getTaskIds());
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "BATCH_OFFLINE", "TASK", null, "批量下线 " + result.getSuccess().size() + " 个任务", null);
        return Result.ok(result);
    }

    @Operation(summary = "定时发布")
    @PostMapping("/{id}/schedule-publish")
    public Result<Void> schedulePublish(@PathVariable Long id, @RequestBody SchedulePublishRequest request) {
        taskService.schedulePublish(id, request.getPublishAt());
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "SCHEDULE_PUBLISH", "TASK", id, task != null ? task.getName() : "任务#" + id, "定时发布: " + request.getPublishAt());
        return Result.ok(null);
    }

    @Operation(summary = "取消定时发布")
    @PostMapping("/{id}/cancel-schedule")
    public Result<Void> cancelSchedule(@PathVariable Long id) {
        taskService.cancelScheduledPublish(id);
        Task task = taskMapper.selectById(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "CANCEL_SCHEDULE", "TASK", id, task != null ? task.getName() : "任务#" + id, null);
        return Result.ok(null);
    }

    @Operation(summary = "复制任务")
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

    @Operation(summary = "获取版本历史")
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

    @Operation(summary = "获取版本详情")
    @GetMapping("/{id}/versions/{versionId}")
    public Result<TaskDefinitionSnapshot> versionDetail(@PathVariable Long id, @PathVariable Long versionId) {
        TaskDefinitionSnapshot snapshot = snapshotMapper.selectById(versionId);
        if (snapshot == null || !snapshot.getTaskId().equals(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本快照不存在");
        }
        return Result.ok(snapshot);
    }

    @Operation(summary = "获取步骤流转配置")
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
