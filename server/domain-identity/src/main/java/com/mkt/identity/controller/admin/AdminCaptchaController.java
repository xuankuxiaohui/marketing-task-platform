package com.mkt.identity.controller.admin;

import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AuthRateLimiter;
import com.mkt.identity.application.CaptchaService;
import com.mkt.identity.response.CaptchaResponse;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.identity.support.ClientIp;
import com.mkt.kernel.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "auth")
public class AdminCaptchaController {

    private final CaptchaService captchas;
    private final AuthRateLimiter rateLimiter;

    public AdminCaptchaController(CaptchaService captchas, AuthRateLimiter rateLimiter) {
        this.captchas = captchas;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/admin/captcha")
    @Operation(summary = "后台验证码")
    public Result<CaptchaResponse> captcha(HttpServletRequest request) {
        rateLimiter.assertIpOnly(ClientIp.of(request), AuthErrorCodes.LOGIN_RATE_LIMITED);
        return Result.ok(captchas.issue(AdminAuthService.CAPTCHA_REALM));
    }
}
