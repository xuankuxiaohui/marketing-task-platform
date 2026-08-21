package com.mkt.admin.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.admin.simulate.SimulateCallbackCommand;
import com.mkt.admin.simulate.SimulateClickCommand;
import com.mkt.admin.simulate.SimulateFlowCommand;
import com.mkt.admin.simulate.SimulateFlowResponse;
import com.mkt.admin.simulate.SimulatePermissions;
import com.mkt.admin.simulate.SimulateProgressCommand;
import com.mkt.admin.simulate.SimulateReverseCommand;
import com.mkt.admin.simulate.SimulateReverseResponse;
import com.mkt.admin.simulate.SimulateStartCommand;
import com.mkt.admin.simulate.SimulateTaskService;
import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.response.TaskStartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/simulate/task")
@Tag(name = "simulate")
public class SimulateAdminController {

    private final SimulateTaskService simulate;

    public SimulateAdminController(SimulateTaskService simulate) {
        this.simulate = simulate;
    }

    @GetMapping("/list")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Operation(summary = "模拟任务列表", description = "权限 simulate:task；C 端可见性")
    public Result<PageData<TaskCardView>> list(
            @RequestParam Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(simulate.list(userId, category, page, pageSize));
    }

    @GetMapping("/detail")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Operation(summary = "模拟任务详情", description = "权限 simulate:task")
    public Result<TaskDetailResponse> detail(@RequestParam Long userId, @RequestParam Long taskId) {
        return Result.ok(simulate.detail(userId, taskId));
    }

    @PostMapping("/start")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Audited(module = "simulate", action = "task-start")
    @Operation(summary = "模拟开始任务", description = "权限 simulate:task；GrantContext.simulated=true")
    public Result<TaskStartResponse> start(
            @Valid @RequestBody SimulateStartCommand command, HttpServletRequest request) {
        return Result.ok(simulate.start(command, ip(request)));
    }

    @PostMapping("/click")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Audited(module = "simulate", action = "task-click")
    @Operation(summary = "模拟点击步骤", description = "权限 simulate:task")
    public Result<TaskClickResponse> click(
            @Valid @RequestBody SimulateClickCommand command, HttpServletRequest request) {
        return Result.ok(simulate.click(command, ip(request)));
    }

    @PostMapping("/callback")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Audited(module = "simulate", action = "task-callback")
    @Operation(summary = "模拟回调步骤", description = "权限 simulate:task；进程内无 HMAC")
    public Result<TaskCallbackResponse> callback(@Valid @RequestBody SimulateCallbackCommand command) {
        return Result.ok(simulate.callback(command));
    }

    @PostMapping("/progress")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Audited(module = "simulate", action = "task-progress")
    @Operation(summary = "模拟进度上报", description = "权限 simulate:task；进程内无 HMAC")
    public Result<TaskProgressResponse> progress(@Valid @RequestBody SimulateProgressCommand command) {
        return Result.ok(simulate.progress(command));
    }

    @PostMapping("/flow")
    @SaCheckPermission(SimulatePermissions.FLOW)
    @Audited(module = "simulate", action = "task-flow")
    @Operation(summary = "一键模拟全流程", description = "权限 simulate:flow")
    public Result<SimulateFlowResponse> flow(
            @Valid @RequestBody SimulateFlowCommand command, HttpServletRequest request) {
        return Result.ok(simulate.flow(command, ip(request)));
    }

    @PostMapping("/reverse")
    @SaCheckPermission(SimulatePermissions.TASK)
    @Audited(module = "simulate", action = "task-reverse")
    @Operation(summary = "模拟冲正", description = "权限 simulate:task；回补库存/反向积分，不调渠道")
    public Result<SimulateReverseResponse> reverse(@Valid @RequestBody SimulateReverseCommand command) {
        return Result.ok(simulate.reverse(command));
    }

    private static String ip(HttpServletRequest request) {
        return request == null ? "127.0.0.1" : request.getRemoteAddr();
    }
}
