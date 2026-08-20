package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DictTypeUpdateCommand(
        @NotBlank @Size(max = 64) String name,
        @NotBlank String status,
        @Size(max = 255) String remark) {}
