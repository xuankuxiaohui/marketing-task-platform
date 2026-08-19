package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskTransitionCommand(
        @NotBlank @Size(max = 64) String fromStepCode,
        @NotBlank @Size(max = 64) String toStepCode,
        @Size(max = 1024) String conditionExpr,
        Integer priority) {}
