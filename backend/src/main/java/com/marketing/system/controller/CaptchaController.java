package com.marketing.system.controller;

import com.marketing.common.Result;
import com.marketing.system.domain.dto.CaptchaResponse;
import com.marketing.security.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CaptchaController {
    private final CaptchaService captchaService;

    @GetMapping("/api/captcha")
    public Result<CaptchaResponse> generate() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return Result.ok(new CaptchaResponse(result.captchaKey(), result.captchaImage()));
    }
}
