package com.marketing.task.domain.dto;

import com.marketing.task.domain.vo.TaskStepPlatformVO;
import com.marketing.task.domain.vo.TaskStepVO;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "C 端任务详情 DTO")
public class TaskInstanceDetailDTO {
    @Schema(description = "用户任务实例")
    private UserTaskInstanceVO instance;
    @Schema(description = "任务步骤列表")
    private List<TaskStepVO> steps;
    @Schema(description = "步骤端特化配置")
    private List<TaskStepPlatformVO> stepPlatforms;
}
