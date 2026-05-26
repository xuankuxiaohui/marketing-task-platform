package com.marketing.task.domain.dto;

import com.marketing.task.domain.vo.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "任务聚合 DTO")
public class TaskAggregateDTO {
    @Valid
    @Schema(description = "任务主体")
    private TaskAdminVO task;
    @Schema(description = "任务步骤列表")
    private List<TaskStepVO> steps;
    @Schema(description = "过滤器列表")
    private List<TaskFilterVO> filters;
    @Schema(description = "端配置列表")
    private List<TaskPlatformVO> platforms;
    @Schema(description = "步骤级端配置列表")
    private List<TaskStepPlatformVO> stepPlatforms;
    @Schema(description = "步骤分支转换列表")
    private List<TaskStepTransitionVO> transitions;
}
