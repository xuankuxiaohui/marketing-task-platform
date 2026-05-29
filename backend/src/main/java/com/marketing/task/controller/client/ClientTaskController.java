package com.marketing.task.controller.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.dto.TaskInstanceDetailDTO;
import com.marketing.task.domain.dto.TaskSnapshotDTO;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.vo.*;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.service.platform.PlatformAdapterRegistry;
import com.marketing.task.common.EventType;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.step.StepAdvanceEngine;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import com.marketing.task.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "Client - Tasks", description = "C端任务")
@RestController
@RequestMapping("/api/client/task")
@RequiredArgsConstructor
public class ClientTaskController {
    private final TaskService taskService;
    private final StepAdvanceEngine stepAdvanceEngine;
    private final TaskStepPlatformMapper taskStepPlatformMapper;
    private final TaskDefinitionCacheService cacheService;
    private final PlatformAdapterRegistry platformAdapterRegistry;
    private final EventTrackingService eventTrackingService;

    @Operation(summary = "获取任务列表")
    @GetMapping("/list")
    public Result<List<TaskClientVO>> list() {
        UserContext userContext = UserContextHolder.get();
        List<TaskClientVO> tasks = taskService.listPublished(userContext);
        for (TaskClientVO task : tasks) {
            eventTrackingService.track(EventType.TASK_VIEWED, task.getId(), null, null,
                    userContext.getUserId(), userContext.getPlatform().name(), Map.of());
        }
        return Result.ok(tasks);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public Result<TaskInstanceDetailDTO> detail(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userContext);
        List<TaskStep> steps = getStepsForInstance(taskId, instance);
        List<TaskStepPlatform> stepPlatforms = getStepPlatformsForInstance(taskId, instance, userContext);

        UserTaskInstanceVO instanceVO = UserTaskInstanceVO.from(instance);

        Map<Long, TaskStepPlatform> platformConfigByStepId = stepPlatforms.stream()
                .collect(Collectors.toMap(TaskStepPlatform::getStepId, Function.identity(), (a, b) -> a));

        var adapter = platformAdapterRegistry.get(userContext.getPlatform());
        List<TaskStepVO> stepVOs = steps.stream().map(step -> {
            TaskStepVO vo = TaskStepVO.from(step);
            TaskStepPlatform config = platformConfigByStepId.get(step.getId());
            TaskStepPlatform rendered = adapter.renderStep(step, config, userContext);
            vo.applyPlatformConfig(rendered);
            return vo;
        }).toList();

        List<TaskStepPlatformVO> spVOs = stepPlatforms.stream().map(TaskStepPlatformVO::from).toList();
        return Result.ok(new TaskInstanceDetailDTO(instanceVO, stepVOs, spVOs));
    }

    @Operation(summary = "开始任务")
    @PostMapping("/{taskId}/start")
    public Result<UserTaskInstanceVO> start(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        return Result.ok(UserTaskInstanceVO.from(taskService.getOrCreateInstance(task, userContext)));
    }

    private List<TaskStep> getStepsForInstance(Long taskId, UserTaskInstance instance) {
        if (instance.getTaskVersion() != null) {
            TaskSnapshotDTO snapshot = cacheService.getSnapshot(taskId, instance.getTaskVersion());
            if (snapshot != null) {
                return snapshot.steps();
            }
        }
        return cacheService.getSteps(taskId);
    }

    private List<TaskStepPlatform> getStepPlatformsForInstance(Long taskId, UserTaskInstance instance,
                                                                UserContext userContext) {
        // Use snapshot's stepPlatforms when available (consistent with getStepsForInstance)
        if (instance.getTaskVersion() != null) {
            TaskSnapshotDTO snapshot = cacheService.getSnapshot(taskId, instance.getTaskVersion());
            if (snapshot != null && !snapshot.stepPlatforms().isEmpty()) {
                return snapshot.stepPlatforms().stream()
                        .filter(sp -> userContext.getPlatform().name().equals(sp.getPlatform()))
                        .toList();
            }
        }
        // Fallback for old snapshots without stepPlatforms: query live table
        return taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId)
                        .eq(TaskStepPlatform::getPlatform, userContext.getPlatform().name()));
    }

    @Operation(summary = "点击步骤")
    @PostMapping("/{taskId}/step/{stepId}/click")
    public Result<UserTaskInstanceVO> click(@PathVariable Long taskId, @PathVariable Long stepId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userContext);
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.click(instance, stepId)));
    }
}
