package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.entity.ClientUser;
import com.marketing.task.service.OperationLogService;
import com.marketing.task.service.auth.ClientUserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/client-users")
@RequiredArgsConstructor
public class ClientUserController {
    private final ClientUserManagementService clientUserManagementService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<IPage<ClientUser>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(clientUserManagementService.list(page, size, keyword));
    }

    @PutMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id) {
        String newPassword = clientUserManagementService.resetPassword(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "RESET_PASSWORD", "CLIENT_USER", id, null, "重置客户端用户密码");
        return Result.ok(newPassword);
    }

    @PutMapping("/{id}/toggle-enabled")
    public Result<Boolean> toggleEnabled(@PathVariable Long id) {
        boolean newState = clientUserManagementService.toggleEnabled(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, newState ? "ENABLE" : "DISABLE", "CLIENT_USER", id, null,
                newState ? "启用客户端用户" : "停用客户端用户并踢下线");
        return Result.ok(newState);
    }

    @PostMapping("/{id}/kick")
    public Result<Void> kick(@PathVariable Long id) {
        clientUserManagementService.kick(id);
        String operatorId = UserContextHolder.get().getUserId();
        operationLogService.record(operatorId, "KICK", "CLIENT_USER", id, null, "强制踢下线");
        return Result.ok(null);
    }
}
