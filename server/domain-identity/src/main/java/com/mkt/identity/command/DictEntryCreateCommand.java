package com.mkt.identity.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DictEntryCreateCommand(
        @NotBlank @Size(max = 64) String typeCode,
        @NotBlank @Size(max = 64) String label,
        @NotBlank @Size(max = 64) String value,
        @NotNull Integer sort,
        @Size(max = 255) String remark) {}
