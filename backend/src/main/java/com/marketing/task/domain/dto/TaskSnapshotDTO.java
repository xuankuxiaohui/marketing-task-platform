package com.marketing.task.domain.dto;

import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;

import java.util.Collections;
import java.util.List;

public record TaskSnapshotDTO(Task task, List<TaskStep> steps, List<TaskFilter> filters,
                               List<TaskPlatform> platforms, List<TaskStepPlatform> stepPlatforms) {
    /**
     * Returns stepPlatforms or an empty list for backward compatibility with old
     * snapshots that were created before stepPlatforms was added to the DTO.
     */
    @Override
    public List<TaskStepPlatform> stepPlatforms() {
        return stepPlatforms != null ? stepPlatforms : Collections.emptyList();
    }
}
