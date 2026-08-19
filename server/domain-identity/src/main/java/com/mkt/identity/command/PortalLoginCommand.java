package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record PortalLoginCommand(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String captchaId,
        @NotBlank String captchaCode) {}
