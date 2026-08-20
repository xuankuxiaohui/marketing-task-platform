package com.mkt.task.convert;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import java.time.Instant;
import java.util.List;

/** Frozen aggregate JSON for {@code task_version_snapshot.content} (design §3.3.6). */
public record SnapshotContent(
        String code,
        String name,
        String description,
        String category,
        String iconUrl,
        String badgeText,
        Instant startTime,
        Instant endTime,
        int sortWeight,
        String cycleType,
        String cronExpr,
        Instant specialStart,
        Instant specialEnd,
        String mutexGroupCode,
        TaskGrayCommand gray,
        TaskFilterCommand filter,
        List<TaskStepCommand> steps,
        List<TaskTransitionCommand> transitions,
        List<TaskActionCommand> actions) {}
