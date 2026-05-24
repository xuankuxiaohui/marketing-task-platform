package com.marketing.task.controller.client;

import com.marketing.task.common.Result;
import com.marketing.task.config.AuthProperties;
import com.marketing.task.domain.dto.LoginRequest;
import com.marketing.task.domain.dto.LoginResponse;
import com.marketing.task.domain.dto.RegisterRequest;
import com.marketing.task.security.CaptchaService;
import com.marketing.task.service.auth.ClientAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
public class ClientAuthController {
    private final ClientAuthService clientAuthService;
    private final CaptchaService captchaService;
    private final AuthProperties authProperties;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        ClientAuthService.LoginResult result = clientAuthService.register(
                new ClientAuthService.RegisterRequest(req.username(), req.password(), req.nickname(),
                        req.province(), req.role(), req.tags(), req.orgId(), req.level()));
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), authProperties.client().expiryMinutes() * 60));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        ClientAuthService.LoginResult result = clientAuthService.login(req.username(), req.password());
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), authProperties.client().expiryMinutes() * 60));
    }
}
