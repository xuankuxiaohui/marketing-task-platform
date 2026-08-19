package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigCreateCommand(
        @NotBlank @Size(max = 128) String configKey,
        @NotBlank @Size(max = 64) String configGroup,
        @NotBlank String configValue,
        @NotBlank String valueType,
        Boolean masked,
        @Size(max = 255) String remark) {}
