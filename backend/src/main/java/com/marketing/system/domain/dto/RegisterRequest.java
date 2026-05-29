package com.marketing.system.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(@NotBlank String username, @NotBlank String password,
                              @NotBlank String captchaKey, @NotBlank String captchaCode,
                              String nickname, String province, String role,
                              String tags, String orgId, Integer level) {
}
