package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.service.OperationLogService;
import com.marketing.task.service.auth.AdminUserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserManagementService adminUserManagementService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<IPage<AdminUser>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminUserManagementService.list(page, size, keyword));
    }

    @PutMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id) {
        String newPassword = adminUserManagementService.resetPassword(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "RESET_PASSWORD", "ADMIN_USER", id, null, "重置后台用户密码");
        return Result.ok(newPassword);
    }

    @PutMapping("/{id}/toggle-enabled")
    public Result<Boolean> toggleEnabled(@PathVariable Long id) {
        boolean newState = adminUserManagementService.toggleEnabled(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, newState ? "ENABLE" : "DISABLE", "ADMIN_USER", id, null,
                newState ? "启用后台用户" : "停用后台用户并踢下线");
        return Result.ok(newState);
    }

    @PostMapping("/{id}/kick")
    public Result<Void> kick(@PathVariable Long id) {
        adminUserManagementService.kick(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "KICK", "ADMIN_USER", id, null, "强制踢下线");
        return Result.ok(null);
    }
}
