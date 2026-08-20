package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;

public record PortalUserResetPasswordCommand(@NotBlank String newPassword) {}
