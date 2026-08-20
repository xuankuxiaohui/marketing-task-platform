package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateCommand(
        @NotBlank @Size(min = 3, max = 30) String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description) {}
