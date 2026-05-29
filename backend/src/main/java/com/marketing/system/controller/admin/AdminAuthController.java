package com.marketing.system.controller.admin;

import com.marketing.common.Result;
import com.marketing.system.domain.dto.AdminLoginRequest;
import com.marketing.system.domain.dto.LoginResponse;
import com.marketing.security.CaptchaService;
import com.marketing.system.service.auth.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${sa-token.timeout:7200}")
    private long tokenTimeout;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        captchaService.verify(req.captchaKey(), req.captchaCode());
        AdminAuthService.LoginResult result = adminAuthService.login(req.username(), req.password());
        return Result.ok(new LoginResponse(result.token(), result.userId(), result.username(),
                result.nickname(), tokenTimeout));
    }
}
