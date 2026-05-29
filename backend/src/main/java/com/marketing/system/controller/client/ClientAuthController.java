package com.marketing.system.controller.client;

import com.marketing.common.Result;
import com.marketing.system.domain.dto.LoginRequest;
import com.marketing.system.domain.dto.LoginResponse;
import com.marketing.system.domain.dto.RegisterRequest;
import com.marketing.security.CaptchaService;
import com.marketing.system.service.auth.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Client - Auth", description = "C端认证")
@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
public class ClientAuthController {
    private final ClientAuthService clientAuthService;
    private final CaptchaService captchaService;

    @Value("${sa-token.timeout:7200}")
    private long tokenTimeout;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        ClientAuthService.LoginResult result = clientAuthService.register(
                new ClientAuthService.RegisterRequest(req.username(), req.password(), req.nickname(),
                        req.province(), req.role(), req.tags(), req.orgId(), req.level()));
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), tokenTimeout, result.province(), result.role(), result.tags(),
                result.orgId(), result.level(), "WEB"));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        ClientAuthService.LoginResult result = clientAuthService.login(req.username(), req.password());
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), tokenTimeout, result.province(), result.role(), result.tags(),
                result.orgId(), result.level(), "WEB"));
    }
}
