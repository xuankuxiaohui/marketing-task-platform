package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marketing.task.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.dto.AdminUserCreateRequest;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.service.OperationLogService;
import com.marketing.task.service.auth.AdminUserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "后台用户管理")
@RestController
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserManagementService adminUserManagementService;
    private final OperationLogService operationLogService;

    @Operation(summary = "查询用户列表")
    @GetMapping
    public Result<IPage<AdminUser>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminUserManagementService.list(page, size, keyword));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<AdminUser> create(@Valid @RequestBody AdminUserCreateRequest request) {
        AdminUser user = adminUserManagementService.create(
                request.getUsername(), request.getPassword(), request.getNickname());
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "CREATE", "ADMIN_USER", user.getId(), user.getUsername(), "创建后台用户");
        user.setPasswordHash(null);
        return Result.ok(user);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id) {
        String newPassword = adminUserManagementService.resetPassword(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "RESET_PASSWORD", "ADMIN_USER", id, null, "重置后台用户密码");
        return Result.ok(newPassword);
    }

    @Operation(summary = "启用/停用用户")
    @PutMapping("/{id}/toggle-enabled")
    public Result<Boolean> toggleEnabled(@PathVariable Long id) {
        boolean newState = adminUserManagementService.toggleEnabled(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, newState ? "ENABLE" : "DISABLE", "ADMIN_USER", id, null,
                newState ? "启用后台用户" : "停用后台用户并踢下线");
        return Result.ok(newState);
    }

    @Operation(summary = "踢下线")
    @PostMapping("/{id}/kick")
    public Result<Void> kick(@PathVariable Long id) {
        adminUserManagementService.kick(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "KICK", "ADMIN_USER", id, null, "强制踢下线");
        return Result.ok(null);
    }
}
