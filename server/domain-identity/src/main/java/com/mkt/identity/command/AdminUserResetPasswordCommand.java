package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record AdminUserResetPasswordCommand(@NotBlank String newPassword) {}
