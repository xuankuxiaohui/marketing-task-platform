package com.marketing.task.controller.client;

import com.marketing.task.common.Result;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.service.step.StepAdvanceEngine;
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

    @GetMapping("/list")
    public Result<List<Task>> list() {
        UserContext userContext = UserContextHolder.get();
        return Result.ok(taskService.listPublished(userContext));
    }

    @GetMapping("/{taskId}")
    public Result<UserTaskInstance> detail(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        return Result.ok(taskService.getOrCreateInstance(task, userContext));
    }

    @PostMapping("/{taskId}/start")
    public Result<UserTaskInstance> start(@PathVariable Long taskId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        return Result.ok(taskService.getOrCreateInstance(task, userContext));
    }

    @PostMapping("/{taskId}/step/{stepId}/click")
    public Result<UserTaskInstance> click(@PathVariable Long taskId, @PathVariable Long stepId) {
        UserContext userContext = UserContextHolder.get();
        Task task = taskService.requireTask(taskId);
        UserTaskInstance instance = taskService.getOrCreateInstance(task, userContext);
        return Result.ok(stepAdvanceEngine.click(instance, stepId));
    }
}
