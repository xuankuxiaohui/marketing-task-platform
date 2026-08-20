package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DictEntryUpdateCommand(
        @NotBlank @Size(max = 64) String label,
        @NotBlank @Size(max = 64) String value,
        @NotNull Integer sort,
        @NotBlank String status,
        @Size(max = 255) String remark) {}
