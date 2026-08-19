package com.mkt.identity.controller.admin;

import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminMenuService;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.command.ChangePasswordCommand;
import com.mkt.identity.domain.DeviceIds;
import com.mkt.identity.response.AdminLoginResponse;
import com.mkt.identity.response.AdminProfileResponse;
import com.mkt.identity.response.MenuNodeResponse;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.support.AuthCookies;
import com.mkt.identity.support.ClientIp;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "auth")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminMenuService menus;

    public AdminAuthController(AdminAuthService authService, AdminMenuService menus) {
        this.authService = authService;
        this.menus = menus;
    }

    @PostMapping("/login")
    @Operation(summary = "后台登录")
    public Result<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginCommand command,
            HttpServletRequest request,
            HttpServletResponse response) {
        AdminAuthService.IssuedAdminSession issued =
                authService.login(command, context(request, command.deviceId())).orThrow();
        AuthCookies.writeSession(response, issued.token());
        AuthCookies.writeCsrf(response, issued.csrfToken());
        return Result.ok(issued.body());
    }

    @PostMapping("/logout")
    @Audited(module = "auth", action = "logout")
    @Operation(summary = "后台登出")
    public Result<OkResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        UserPrincipal principal = UserContext.require();
        authService.logout(
                principal.userId(),
                principal.username(),
                AuthCookies.read(request, AuthCookies.SESSION),
                context(request, null));
        AuthCookies.clear(response);
        return Result.ok(OkResponse.yes());
    }

    @GetMapping("/menus")
    @Operation(summary = "当前用户菜单树", description = "登录态；仅返回有权菜单及其子树（R2.4）")
    public Result<List<MenuNodeResponse>> menus() {
        return Result.ok(menus.menus(UserContext.require().userId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "当前用户资料", description = "登录态；空角色用户可用（R3.2）")
    public Result<AdminProfileResponse> profile() {
        return Result.ok(menus.profile(UserContext.require().userId()));
    }

    @PutMapping("/password")
    @Audited(module = "auth", action = "password")
    @Operation(summary = "修改个人密码")
    public Result<OkResponse> changePassword(
            @Valid @RequestBody ChangePasswordCommand command, HttpServletRequest request) {
        authService.changePassword(
                UserContext.require().userId(), command, AuthCookies.read(request, AuthCookies.SESSION));
        return Result.ok(OkResponse.yes());
    }

    private static AuthAttemptContext context(HttpServletRequest request, String deviceId) {
        return new AuthAttemptContext(
                ClientIp.of(request), request.getHeader("User-Agent"), DeviceIds.normalizeOrNull(deviceId));
    }
}
