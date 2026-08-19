package com.mkt.task.response;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import java.time.Instant;
import java.util.List;

public record TaskDefinitionAggregateResponse(
        long id,
        String code,
        String name,
        String description,
        String category,
        String iconUrl,
        String badgeText,
        Instant startTime,
        Instant endTime,
        int sortWeight,
        String status,
        int version,
        int currentVersion,
        boolean pendingRevision,
        String cycleType,
        String cronExpr,
        Instant specialStart,
        Instant specialEnd,
        Long mutexGroupId,
        TaskGrayCommand gray,
        TaskFilterCommand filter,
        List<TaskStepCommand> steps,
        List<TaskTransitionCommand> transitions,
        List<TaskActionCommand> actions) {}
