package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.OkResponse;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import com.mkt.task.response.TaskDefinitionView;
import com.mkt.task.support.TaskPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/task/definitions")
@Tag(name = "task")
public class TaskDefinitionAdminController {

    private final TaskDefinitionAppService appService;

    public TaskDefinitionAdminController(TaskDefinitionAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(TaskPermissions.DEFINITION_QUERY)
    @Operation(summary = "分页查询任务定义", description = "权限 task:definition:query")
    public Result<PageData<TaskDefinitionView>> page(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        TaskDefinitionQuery query =
                new TaskDefinitionQuery(code, name, status, category, PageQuery.of(page, pageSize));
        return Result.ok(appService.page(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(TaskPermissions.DEFINITION_QUERY)
    @Operation(summary = "任务定义聚合详情", description = "权限 task:definition:query")
    public Result<TaskDefinitionAggregateResponse> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping("/save-aggregate")
    @SaCheckPermission(
            value = {TaskPermissions.DEFINITION_CREATE, TaskPermissions.DEFINITION_UPDATE},
            mode = SaMode.OR)
    @Audited(module = "task", action = "definition-save")
    @Operation(
            summary = "聚合保存任务定义",
            description = "新建 task:definition:create / 更新 task:definition:update；原子保存 R11.4")
    public Result<TaskDefinitionSaveResponse> save(@Valid @RequestBody TaskDefinitionSaveCommand command) {
        if (command.id() == null) {
            StpUtil.checkPermission(TaskPermissions.DEFINITION_CREATE);
        } else {
            StpUtil.checkPermission(TaskPermissions.DEFINITION_UPDATE);
        }
        return Result.ok(appService.saveAggregate(command));
    }

    @PostMapping("/{id}/copy")
    @SaCheckPermission(TaskPermissions.DEFINITION_COPY)
    @Audited(module = "task", action = "definition-copy")
    @Operation(summary = "复制任务定义", description = "权限 task:definition:copy")
    public Result<TaskDefinitionSaveResponse> copy(
            @PathVariable long id, @Valid @RequestBody TaskCopyCommand command) {
        return Result.ok(appService.copy(id, command));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(TaskPermissions.DEFINITION_DELETE)
    @Audited(module = "task", action = "definition-delete")
    @Operation(summary = "逻辑删除任务定义", description = "权限 task:definition:delete；已发布 400")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
