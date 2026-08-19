package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginCommand(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String captchaId,
        @NotBlank String captchaCode,
        String deviceId) {}
