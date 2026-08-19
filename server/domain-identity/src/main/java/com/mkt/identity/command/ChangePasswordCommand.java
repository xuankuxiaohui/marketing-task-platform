package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordCommand(@NotBlank String oldPassword, @NotBlank String newPassword) {}
