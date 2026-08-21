package com.mkt.task.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InternalProgressCommand(
        Long instanceId,
        Long userId,
        @Size(max = 64) String taskCode,
        @Size(max = 40) String cycleKey,
        @NotBlank @Size(max = 64) String stepCode,
        @NotNull @Min(1) @Max(1000) Integer value,
        @NotBlank @Size(max = 64) String reportId) {}
