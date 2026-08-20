package com.mkt.task.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalCallbackCommand(
        Long instanceId,
        Long userId,
        @Size(max = 64) String taskCode,
        @Size(max = 40) String cycleKey,
        @NotBlank @Size(max = 64) String stepCode,
        @Size(max = 64) String bizNo) {}
