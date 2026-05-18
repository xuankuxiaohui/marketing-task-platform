package com.marketing.task.domain.dto;

import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.entity.TaskStep;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class TaskAggregateDTO {
    @Valid
    private Task task;
    private List<TaskStep> steps;
    private List<TaskFilter> filters;
    private List<TaskPlatform> platforms;
}
