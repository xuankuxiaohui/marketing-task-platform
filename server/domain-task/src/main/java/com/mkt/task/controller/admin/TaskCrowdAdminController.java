package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskCrowdAppService;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.response.IdResponse;
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
@RequestMapping("/admin/task/crowds")
@Tag(name = "task")
public class TaskCrowdAdminController {

    private final TaskCrowdAppService appService;

    public TaskCrowdAdminController(TaskCrowdAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(TaskPermissions.CROWD_QUERY)
    @Operation(summary = "分页查询人群包", description = "权限 task:crowd:query")
    public Result<PageData<CrowdResponse>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(PageQuery.of(page, pageSize)));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(TaskPermissions.CROWD_QUERY)
    @Operation(summary = "人群包详情", description = "权限 task:crowd:query")
    public Result<CrowdResponse> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping
    @SaCheckPermission(TaskPermissions.CROWD_CREATE)
    @Audited(module = "task", action = "crowd-create")
    @Operation(summary = "新建人群包", description = "权限 task:crowd:create")
    public Result<IdResponse> create(@Valid @RequestBody CrowdSaveCommand command) {
        return Result.ok(new IdResponse(appService.create(command).id()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(TaskPermissions.CROWD_UPDATE)
    @Audited(module = "task", action = "crowd-update")
    @Operation(summary = "更新人群包", description = "权限 task:crowd:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody CrowdSaveCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(TaskPermissions.CROWD_DELETE)
    @Audited(module = "task", action = "crowd-delete")
    @Operation(summary = "删除人群包", description = "权限 task:crowd:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/import")
    @SaCheckPermission(TaskPermissions.CROWD_UPDATE)
    @Audited(module = "task", action = "crowd-import")
    @Operation(summary = "导入人群包用户", description = "权限 task:crowd:update；出参 imported/deduplicated/invalid")
    public Result<CrowdImportResponse> importUsers(
            @PathVariable long id, @Valid @RequestBody CrowdImportCommand command) {
        return Result.ok(appService.importUsers(id, command));
    }
}
