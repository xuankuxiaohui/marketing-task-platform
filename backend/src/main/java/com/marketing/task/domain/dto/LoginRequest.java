package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password,
                           @NotBlank String captchaKey, @NotBlank String captchaCode) {
}
