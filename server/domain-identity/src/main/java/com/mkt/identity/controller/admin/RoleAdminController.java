package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.RoleAppService;
import com.mkt.identity.command.RoleAssignPermissionsCommand;
import com.mkt.identity.command.RoleCreateCommand;
import com.mkt.identity.command.RoleUpdateCommand;
import com.mkt.identity.query.RoleQuery;
import com.mkt.identity.response.IdResponse;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.response.RoleView;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
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
@RequestMapping("/admin/identity/roles")
@Tag(name = "identity")
public class RoleAdminController {

    private final RoleAppService appService;

    public RoleAdminController(RoleAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.ROLE_QUERY)
    @Operation(summary = "分页查询角色", description = "权限 identity:role:query；all=true 返回启用全量")
    public Result<PageData<RoleView>> page(
            @RequestParam(required = false) Boolean all,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(new RoleQuery(Boolean.TRUE.equals(all), PageQuery.of(page, pageSize))));
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.ROLE_CREATE)
    @Audited(module = "identity", action = "role-create")
    @Operation(summary = "创建角色", description = "权限 identity:role:create")
    public Result<IdResponse> create(@Valid @RequestBody RoleCreateCommand command) {
        return Result.ok(new IdResponse(appService.create(command).getId()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.ROLE_UPDATE)
    @Audited(module = "identity", action = "role-update")
    @Operation(summary = "更新角色", description = "权限 identity:role:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody RoleUpdateCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.ROLE_DELETE)
    @Audited(module = "identity", action = "role-delete")
    @Operation(summary = "删除角色", description = "权限 identity:role:delete；内置拒绝 auth.role.built-in")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }

    @PutMapping("/{id}/permissions")
    @SaCheckPermission(IdentityPermissions.ROLE_ASSIGN_PERMISSION)
    @Audited(module = "identity", action = "role-assign-permission")
    @Operation(summary = "分配角色权限", description = "权限 identity:role:assign-permission；内置拒绝 auth.role.built-in")
    public Result<OkResponse> assignPermissions(
            @PathVariable long id, @Valid @RequestBody RoleAssignPermissionsCommand command) {
        appService.assignPermissions(id, command);
        return Result.ok(OkResponse.yes());
    }
}
