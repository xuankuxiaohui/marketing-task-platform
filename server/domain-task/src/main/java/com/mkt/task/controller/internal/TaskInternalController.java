package com.mkt.task.controller.internal;

import com.mkt.kernel.Result;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/task")
@Tag(name = "internal-task")
public class TaskInternalController {

    private final TaskStepAppService steps;

    public TaskInternalController(TaskStepAppService steps) {
        this.steps = steps;
    }

    @PostMapping("/callback")
    @Operation(summary = "CALLBACK 步骤回调", description = "HMAC 四头；bizNo 覆盖 last_biz_no")
    public Result<TaskCallbackResponse> callback(@Valid @RequestBody InternalCallbackCommand command) {
        return Result.ok(steps.callback(command));
    }

    @PostMapping("/progress")
    @Operation(summary = "PROGRESS 进度上报", description = "HMAC 四头；reportId 去重")
    public Result<TaskProgressResponse> progress(@Valid @RequestBody InternalProgressCommand command) {
        return Result.ok(steps.progress(command));
    }
}
