package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskMutexGroupAppService;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.response.IdResponse;
import com.mkt.task.response.MutexGroupResponse;
import com.mkt.task.response.OkResponse;
import com.mkt.task.support.TaskPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/task/mutex-groups")
@Tag(name = "task")
public class TaskMutexGroupAdminController {

    private final TaskMutexGroupAppService appService;

    public TaskMutexGroupAdminController(TaskMutexGroupAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(TaskPermissions.MUTEX_QUERY)
    @Operation(summary = "分页查询互斥组", description = "权限 task:mutex-group:query")
    public Result<PageData<MutexGroupResponse>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(PageQuery.of(page, pageSize)));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(TaskPermissions.MUTEX_QUERY)
    @Operation(summary = "互斥组详情", description = "权限 task:mutex-group:query")
    public Result<MutexGroupResponse> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping
    @SaCheckPermission(TaskPermissions.MUTEX_CREATE)
    @Audited(module = "task", action = "mutex-create")
    @Operation(summary = "新建互斥组", description = "权限 task:mutex-group:create")
    public Result<IdResponse> create(@Valid @RequestBody MutexGroupSaveCommand command) {
        return Result.ok(new IdResponse(appService.create(command).id()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(TaskPermissions.MUTEX_UPDATE)
    @Audited(module = "task", action = "mutex-update")
    @Operation(summary = "更新互斥组", description = "权限 task:mutex-group:update")
    public Result<OkResponse> update(
            @PathVariable long id, @Valid @RequestBody MutexGroupSaveCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(TaskPermissions.MUTEX_DELETE)
    @Audited(module = "task", action = "mutex-delete")
    @Operation(summary = "删除互斥组", description = "权限 task:mutex-group:delete；被引用 400 task.mutex.in-use")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
