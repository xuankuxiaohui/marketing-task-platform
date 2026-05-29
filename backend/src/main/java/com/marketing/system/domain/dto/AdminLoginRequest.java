package com.marketing.system.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String username, @NotBlank String password,
                                @NotBlank String captchaKey, @NotBlank String captchaCode) {
}
