package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpressionValidateCommand(
        @NotBlank @Size(max = 1_048_576) String expression, @NotBlank String type) {}
