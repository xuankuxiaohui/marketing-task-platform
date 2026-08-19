package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.AdminUserAppService;
import com.mkt.identity.command.AdminUserCreateCommand;
import com.mkt.identity.command.AdminUserResetPasswordCommand;
import com.mkt.identity.command.AdminUserUpdateCommand;
import com.mkt.identity.query.AdminUserQuery;
import com.mkt.identity.response.AdminUserView;
import com.mkt.identity.response.IdResponse;
import com.mkt.identity.response.OkResponse;
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
@RequestMapping("/admin/identity/users")
@Tag(name = "identity")
public class UserAdminController {

    private final AdminUserAppService appService;

    public UserAdminController(AdminUserAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_QUERY)
    @Operation(summary = "分页查询后台用户", description = "权限 identity:admin-user:query；默认过滤已删除")
    public Result<PageData<AdminUserView>> page(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(
                new AdminUserQuery(username, nickname, status, roleId, PageQuery.of(page, pageSize))));
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_CREATE)
    @Audited(module = "identity", action = "admin-user-create")
    @Operation(summary = "创建后台用户", description = "权限 identity:admin-user:create")
    public Result<IdResponse> create(@Valid @RequestBody AdminUserCreateCommand command) {
        return Result.ok(new IdResponse(appService.create(command).getId()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_UPDATE)
    @Audited(module = "identity", action = "admin-user-update")
    @Operation(summary = "更新后台用户", description = "权限 identity:admin-user:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody AdminUserUpdateCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_DISABLE)
    @Audited(module = "identity", action = "admin-user-disable")
    @Operation(summary = "停用后台用户", description = "权限 identity:admin-user:disable")
    public Result<OkResponse> disable(@PathVariable long id) {
        appService.disable(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_DISABLE)
    @Audited(module = "identity", action = "admin-user-enable")
    @Operation(summary = "启用后台用户", description = "权限 identity:admin-user:disable")
    public Result<OkResponse> enable(@PathVariable long id) {
        appService.enable(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/reset-password")
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_RESET_PASSWORD)
    @Audited(module = "identity", action = "admin-user-reset-password")
    @Operation(summary = "重置后台用户密码", description = "权限 identity:admin-user:reset-password")
    public Result<OkResponse> resetPassword(
            @PathVariable long id, @Valid @RequestBody AdminUserResetPasswordCommand command) {
        appService.resetPassword(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.ADMIN_USER_DELETE)
    @Audited(module = "identity", action = "admin-user-delete")
    @Operation(summary = "逻辑删除后台用户", description = "权限 identity:admin-user:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
