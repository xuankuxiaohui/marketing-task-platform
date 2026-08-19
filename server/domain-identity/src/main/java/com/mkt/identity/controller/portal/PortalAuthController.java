package com.mkt.identity.controller.portal;

import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.application.PortalAuthService;
import com.mkt.identity.command.PortalLoginCommand;
import com.mkt.identity.command.PortalRegisterCommand;
import com.mkt.identity.domain.DeviceIds;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.response.PortalAuthResponse;
import com.mkt.identity.response.UsernameAvailableResponse;
import com.mkt.identity.support.AuthCookies;
import com.mkt.identity.support.ClientIp;
import com.mkt.kernel.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/auth")
@Tag(name = "auth")
public class PortalAuthController {

    private final PortalAuthService authService;

    public PortalAuthController(PortalAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/username-available")
    @Operation(summary = "用户名可用性")
    public Result<UsernameAvailableResponse> usernameAvailable(
            @RequestParam String username, HttpServletRequest request) {
        return Result.ok(authService.usernameAvailable(username, ClientIp.of(request)));
    }

    @PostMapping("/register")
    @Operation(summary = "门户注册")
    public Result<PortalAuthResponse> register(
            @Valid @RequestBody PortalRegisterCommand command,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        return Result.ok(authService.register(command, context(request, deviceId)));
    }

    @PostMapping("/login")
    @Operation(summary = "门户登录")
    public Result<PortalAuthResponse> login(
            @Valid @RequestBody PortalLoginCommand command,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        return Result.ok(authService.login(command, context(request, deviceId)).orThrow());
    }

    @PostMapping("/logout")
    @Operation(summary = "门户登出")
    public Result<OkResponse> logout(HttpServletRequest request) {
        authService.logout(AuthCookies.readBearer(request));
        return Result.ok(OkResponse.yes());
    }

    private static AuthAttemptContext context(HttpServletRequest request, String deviceId) {
        return new AuthAttemptContext(ClientIp.of(request), request.getHeader("User-Agent"), DeviceIds.normalizeOrNull(deviceId));
    }
}
