package com.marketing.task.domain.dto;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.entity.UserTaskInstance;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskInstanceDetailDTO {
    private UserTaskInstance instance;
    private List<TaskStep> steps;
    private List<TaskStepPlatform> stepPlatforms;
}
