package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskExpressionAppService;
import com.mkt.task.command.ExpressionValidateCommand;
import com.mkt.task.response.ExpressionValidateResponse;
import com.mkt.task.support.TaskPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/task/expressions")
@Tag(name = "task")
public class TaskExpressionAdminController {

    private final TaskExpressionAppService appService;

    public TaskExpressionAdminController(TaskExpressionAppService appService) {
        this.appService = appService;
    }

    @PostMapping("/validate")
    @SaCheckPermission(TaskPermissions.EXPRESSION_VALIDATE)
    @Audited(module = "task", action = "expression-validate")
    @Operation(summary = "校验过滤/分支表达式", description = "权限 task:expression:validate；空值语义模拟 R11.5")
    public Result<ExpressionValidateResponse> validate(@Valid @RequestBody ExpressionValidateCommand command) {
        return Result.ok(appService.validate(command));
    }
}
