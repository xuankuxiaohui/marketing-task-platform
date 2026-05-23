package com.marketing.task.controller.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.dto.TaskInstanceDetailDTO;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.vo.*;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.service.step.StepAdvanceEngine;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import com.marketing.task.service.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/task")
@RequiredArgsConstructor
public class ClientTaskController {
    private final TaskService taskService;
    private final StepAdvanceEngine stepAdvanceEngine;
    private final TaskStepPlatformMapper taskStepPlatformMapper;
    private final TaskDefinitionCacheService cacheService;

    @GetMapping("/list")
    public Result<List<TaskClientVO>> list() {
        UserContext userContext = UserContextHolder.get();
        return Result.ok(taskService.listPublished(userContext));
    }

    @GetMapping("/{taskId}")
    public Result<TaskInstanceDetailDTO> detail(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userContext);
        List<TaskStep> steps = cacheService.getSteps(taskId);
        List<TaskStepPlatform> stepPlatforms = taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId)
                        .eq(TaskStepPlatform::getPlatform, userContext.getPlatform().name()));

        UserTaskInstanceVO instanceVO = UserTaskInstanceVO.from(instance);
        List<TaskStepVO> stepVOs = steps.stream().map(TaskStepVO::from).toList();
        List<TaskStepPlatformVO> spVOs = stepPlatforms.stream().map(TaskStepPlatformVO::from).toList();

        return Result.ok(new TaskInstanceDetailDTO(instanceVO, stepVOs, spVOs));
    }

    @PostMapping("/{taskId}/start")
    public Result<UserTaskInstanceVO> start(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        return Result.ok(UserTaskInstanceVO.from(taskService.getOrCreateInstance(task, userContext)));
    }

    @PostMapping("/{taskId}/step/{stepId}/click")
    public Result<UserTaskInstanceVO> click(@PathVariable Long taskId, @PathVariable Long stepId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userContext);
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.click(instance, stepId)));
    }
}
