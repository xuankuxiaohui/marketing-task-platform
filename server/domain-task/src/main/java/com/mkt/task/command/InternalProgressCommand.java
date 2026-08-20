package com.mkt.task.command;

import jakarta.validation.constraints.Size;

public record InternalProgressCommand(
        Long instanceId,
        Long userId,
        @Size(max = 64) String taskCode,
        @Size(max = 40) String cycleKey,
        @Size(max = 64) String stepCode,
        Integer value,
        @Size(max = 64) String reportId) {}
