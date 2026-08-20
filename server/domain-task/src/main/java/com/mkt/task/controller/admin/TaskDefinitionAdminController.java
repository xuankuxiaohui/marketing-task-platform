package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskPublishAppService;
import com.mkt.task.command.BatchIdsCommand;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.OkResponse;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import com.mkt.task.response.TaskDefinitionView;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
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
    private final TaskPublishAppService publishes;

    public TaskDefinitionAdminController(TaskDefinitionAppService appService, TaskPublishAppService publishes) {
        this.appService = appService;
        this.publishes = publishes;
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

    @GetMapping("/schedule-failures")
    @SaCheckPermission(TaskPermissions.DEFINITION_QUERY)
    @Operation(summary = "定时发布失败记录", description = "权限 task:definition:query；过滤审计 schedule-publish-failure")
    public Result<PageData<ScheduleFailureView>> scheduleFailures(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(publishes.scheduleFailures(PageQuery.of(page, pageSize)));
    }

    @PostMapping("/{id}/publish")
    @SaCheckPermission(TaskPermissions.DEFINITION_PUBLISH)
    @Audited(module = "task", action = "definition-publish")
    @Operation(summary = "发布任务", description = "权限 task:definition:publish；修订二次确认 R12.7")
    public Result<PublishResponse> publish(@PathVariable long id, @RequestBody(required = false) PublishCommand command) {
        return Result.ok(publishes.publish(id, command));
    }

    @PostMapping("/{id}/schedule")
    @SaCheckPermission(TaskPermissions.DEFINITION_SCHEDULE)
    @Audited(module = "task", action = "definition-schedule")
    @Operation(summary = "设定时发布", description = "权限 task:definition:schedule")
    public Result<PublishResponse> schedule(@PathVariable long id, @Valid @RequestBody ScheduleCommand command) {
        return Result.ok(publishes.schedule(id, command));
    }

    @PostMapping("/{id}/cancel-schedule")
    @SaCheckPermission(TaskPermissions.DEFINITION_SCHEDULE)
    @Audited(module = "task", action = "definition-cancel-schedule")
    @Operation(summary = "取消定时发布", description = "权限 task:definition:schedule")
    public Result<PublishResponse> cancelSchedule(@PathVariable long id) {
        return Result.ok(publishes.cancelSchedule(id));
    }

    @PostMapping("/{id}/offline")
    @SaCheckPermission(TaskPermissions.DEFINITION_OFFLINE)
    @Audited(module = "task", action = "definition-offline")
    @Operation(summary = "下线任务", description = "权限 task:definition:offline")
    public Result<PublishResponse> offline(@PathVariable long id) {
        return Result.ok(publishes.offline(id));
    }

    @PostMapping("/{id}/reset-revision")
    @SaCheckPermission(TaskPermissions.DEFINITION_UPDATE)
    @Audited(module = "task", action = "definition-reset-revision")
    @Operation(summary = "放弃修订", description = "权限 task:definition:update；从当前版本快照重置编辑态")
    public Result<PublishResponse> resetRevision(@PathVariable long id) {
        return Result.ok(publishes.resetRevision(id));
    }

    @PostMapping("/batch-publish")
    @SaCheckPermission(TaskPermissions.DEFINITION_PUBLISH)
    @Audited(module = "task", action = "definition-batch-publish")
    @Operation(summary = "批量发布", description = "权限 task:definition:publish；单批 ≤50")
    public Result<java.util.List<BatchItemResponse>> batchPublish(@Valid @RequestBody BatchIdsCommand command) {
        return Result.ok(publishes.batchPublish(command.ids()));
    }

    @PostMapping("/batch-offline")
    @SaCheckPermission(TaskPermissions.DEFINITION_OFFLINE)
    @Audited(module = "task", action = "definition-batch-offline")
    @Operation(summary = "批量下线", description = "权限 task:definition:offline；单批 ≤50")
    public Result<java.util.List<BatchItemResponse>> batchOffline(@Valid @RequestBody BatchIdsCommand command) {
        return Result.ok(publishes.batchOffline(command.ids()));
    }

    @GetMapping("/{id}/versions")
    @SaCheckPermission(TaskPermissions.DEFINITION_QUERY)
    @Operation(summary = "版本历史", description = "权限 task:definition:query")
    public Result<java.util.List<TaskVersionView>> versions(@PathVariable long id) {
        return Result.ok(publishes.versions(id));
    }

    @GetMapping("/{id}/versions/diff")
    @SaCheckPermission(TaskPermissions.DEFINITION_QUERY)
    @Operation(summary = "版本对比", description = "权限 task:definition:query")
    public Result<VersionDiffResponse> diff(
            @PathVariable long id, @RequestParam Integer left, @RequestParam Integer right) {
        return Result.ok(publishes.diff(id, left, right));
    }
}
