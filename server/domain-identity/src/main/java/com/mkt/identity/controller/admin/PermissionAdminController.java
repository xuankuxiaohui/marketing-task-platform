package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.PermissionAppService;
import com.mkt.identity.command.PermissionSaveCommand;
import com.mkt.identity.response.IdResponse;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.response.PermissionTreeNodeResponse;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/identity/permissions")
@Tag(name = "identity")
public class PermissionAdminController {

    private final PermissionAppService appService;

    public PermissionAdminController(PermissionAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/tree")
    @SaCheckPermission(IdentityPermissions.PERMISSION_QUERY)
    @Operation(summary = "权限树", description = "权限 identity:permission:query；菜单种子 = §4.10")
    public Result<List<PermissionTreeNodeResponse>> tree() {
        return Result.ok(appService.tree());
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.PERMISSION_CREATE)
    @Audited(module = "identity", action = "permission-create")
    @Operation(summary = "创建权限节点", description = "权限 identity:permission:create；OPERATION 必填 域:资源:操作")
    public Result<IdResponse> create(@Valid @RequestBody PermissionSaveCommand command) {
        return Result.ok(new IdResponse(appService.create(command).getId()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.PERMISSION_UPDATE)
    @Audited(module = "identity", action = "permission-update")
    @Operation(summary = "更新权限节点", description = "权限 identity:permission:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody PermissionSaveCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.PERMISSION_DELETE)
    @Audited(module = "identity", action = "permission-delete")
    @Operation(summary = "删除权限节点", description = "权限 identity:permission:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
