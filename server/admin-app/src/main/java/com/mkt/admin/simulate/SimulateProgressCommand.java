package com.mkt.admin.simulate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimulateProgressCommand(
        @NotNull Long userId,
        @NotNull Long instanceId,
        @NotBlank @Size(max = 64) String stepCode,
        @NotNull @Min(1) @Max(1000) Integer value,
        @NotBlank @Size(max = 64) String reportId) {}
