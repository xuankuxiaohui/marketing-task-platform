package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.PortalUserAppService;
import com.mkt.identity.command.PortalUserProfileCommand;
import com.mkt.identity.command.PortalUserResetPasswordCommand;
import com.mkt.identity.query.PortalUserQuery;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.response.PortalUserDetailResponse;
import com.mkt.identity.response.PortalUserView;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
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
@RequestMapping("/admin/identity/portal-users")
@Tag(name = "identity")
public class PortalUserAdminController {

    private final PortalUserAppService appService;

    public PortalUserAdminController(PortalUserAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_QUERY)
    @Operation(summary = "分页查询门户用户", description = "权限 identity:portal-user:query")
    public Result<PageData<PortalUserView>> page(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant registeredFrom,
            @RequestParam(required = false) Instant registeredTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(new PortalUserQuery(
                username,
                nickname,
                province,
                level,
                tag,
                status,
                registeredFrom,
                registeredTo,
                PageQuery.of(page, pageSize))));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_QUERY)
    @Operation(summary = "门户用户详情", description = "权限 identity:portal-user:query；R5.6 经跨域端口聚合")
    public Result<PortalUserDetailResponse> detail(@PathVariable long id) {
        return Result.ok(appService.detail(id));
    }

    @PutMapping("/{id}/profile")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_UPDATE_PROFILE)
    @Audited(module = "identity", action = "portal-user-update-profile")
    @Operation(summary = "覆盖式编辑门户档案", description = "权限 identity:portal-user:update-profile")
    public Result<OkResponse> updateProfile(
            @PathVariable long id, @Valid @RequestBody PortalUserProfileCommand command) {
        appService.updateProfile(id, command);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_DISABLE)
    @Audited(module = "identity", action = "portal-user-disable")
    @Operation(summary = "停用门户用户", description = "权限 identity:portal-user:disable")
    public Result<OkResponse> disable(@PathVariable long id) {
        appService.disable(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_DISABLE)
    @Audited(module = "identity", action = "portal-user-enable")
    @Operation(summary = "启用门户用户", description = "权限 identity:portal-user:disable")
    public Result<OkResponse> enable(@PathVariable long id) {
        appService.enable(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/reset-password")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_RESET_PASSWORD)
    @Audited(module = "identity", action = "portal-user-reset-password")
    @Operation(summary = "重置门户用户密码", description = "权限 identity:portal-user:reset-password")
    public Result<OkResponse> resetPassword(
            @PathVariable long id, @Valid @RequestBody PortalUserResetPasswordCommand command) {
        appService.resetPassword(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.PORTAL_USER_DELETE)
    @Audited(module = "identity", action = "portal-user-delete")
    @Operation(summary = "逻辑删除门户用户", description = "权限 identity:portal-user:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
