package com.marketing.task.controller.admin;

import com.marketing.task.common.Result;
import com.marketing.task.config.AuthProperties;
import com.marketing.task.domain.dto.AdminLoginRequest;
import com.marketing.task.domain.dto.LoginResponse;
import com.marketing.task.security.CaptchaService;
import com.marketing.task.service.auth.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    private final CaptchaService captchaService;
    private final AuthProperties authProperties;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        AdminAuthService.LoginResult result = adminAuthService.login(req.username(), req.password());
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), authProperties.admin().expiryMinutes() * 60));
    }
}
