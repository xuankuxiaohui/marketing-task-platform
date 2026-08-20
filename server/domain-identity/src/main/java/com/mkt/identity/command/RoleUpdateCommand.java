package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleUpdateCommand(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description,
        @NotBlank @Size(max = 16) String status) {}
