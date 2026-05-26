package com.marketing.task.domain.dto;

import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepTransition;

import java.util.List;

public record TaskSnapshotDTO(Task task, List<TaskStep> steps, List<TaskFilter> filters,
                               List<TaskPlatform> platforms,
                               List<TaskStepTransition> transitions) {
    public TaskSnapshotDTO(Task task, List<TaskStep> steps, List<TaskFilter> filters,
                           List<TaskPlatform> platforms) {
        this(task, steps, filters, platforms, null);
    }
}
