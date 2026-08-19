package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.InternalAppAppService;
import com.mkt.identity.command.InternalAppCreateCommand;
import com.mkt.identity.response.InternalAppCreatedResponse;
import com.mkt.identity.response.InternalAppRotateResponse;
import com.mkt.identity.response.InternalAppView;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/identity/internal-apps")
@Tag(name = "identity")
public class InternalAppAdminController {

    private final InternalAppAppService appService;

    public InternalAppAdminController(InternalAppAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.INTERNAL_APP_QUERY)
    @Operation(summary = "分页查询 internal 调用方", description = "权限 identity:internal-app:query；secret 永不回显")
    public Result<PageData<InternalAppView>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(PageQuery.of(page, pageSize)));
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.INTERNAL_APP_ADD)
    @Audited(module = "identity", action = "internal-app-add")
    @Operation(summary = "登记 internal 调用方", description = "权限 identity:internal-app:add；secret 仅此一次回显")
    public Result<InternalAppCreatedResponse> create(@Valid @RequestBody InternalAppCreateCommand command) {
        return Result.ok(appService.create(command));
    }

    @PostMapping("/{id}/rotate-secret")
    @SaCheckPermission(IdentityPermissions.INTERNAL_APP_EDIT)
    @Audited(module = "identity", action = "internal-app-rotate")
    @Operation(summary = "轮换 secret", description = "权限 identity:internal-app:edit；旧密钥 24h 双活")
    public Result<InternalAppRotateResponse> rotate(@PathVariable long id) {
        return Result.ok(appService.rotate(id));
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission(IdentityPermissions.INTERNAL_APP_EDIT)
    @Audited(module = "identity", action = "internal-app-disable")
    @Operation(summary = "停用调用方", description = "权限 identity:internal-app:edit")
    public Result<OkResponse> disable(@PathVariable long id) {
        appService.disable(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission(IdentityPermissions.INTERNAL_APP_EDIT)
    @Audited(module = "identity", action = "internal-app-enable")
    @Operation(summary = "启用调用方", description = "权限 identity:internal-app:edit")
    public Result<OkResponse> enable(@PathVariable long id) {
        appService.enable(id);
        return Result.ok(OkResponse.yes());
    }
}
