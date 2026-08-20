package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskStepCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull Integer seq,
        @NotBlank @Size(max = 16) String type,
        Integer progressTarget,
        Long prizeId) {}
