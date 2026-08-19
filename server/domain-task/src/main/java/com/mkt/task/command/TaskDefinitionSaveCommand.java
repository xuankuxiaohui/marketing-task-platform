package com.mkt.task.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record TaskDefinitionSaveCommand(
        Long id,
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description,
        @Size(max = 64) String category,
        @Size(max = 512) String iconUrl,
        @Size(max = 16) String badgeText,
        Instant startTime,
        Instant endTime,
        Integer sortWeight,
        @NotBlank @Size(max = 16) String cycleType,
        @Size(max = 32) String cronExpr,
        Instant specialStart,
        Instant specialEnd,
        Long mutexGroupId,
        TaskGrayCommand gray,
        TaskFilterCommand filter,
        @Valid List<TaskStepCommand> steps,
        @Valid List<TaskTransitionCommand> transitions,
        @Valid List<TaskActionCommand> actions) {}
