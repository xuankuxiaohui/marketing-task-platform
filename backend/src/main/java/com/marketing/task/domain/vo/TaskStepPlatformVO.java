package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskStepPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "步骤端特化配置 VO")
public class TaskStepPlatformVO {
    private Long id;
    private Long stepId;
    private String platform;
    private String buttonText;
    private String jumpType;
    private String jumpTarget;

    public static TaskStepPlatformVO from(TaskStepPlatform entity) {
        if (entity == null) return null;
        TaskStepPlatformVO vo = new TaskStepPlatformVO();
        vo.setId(entity.getId());
        vo.setStepId(entity.getStepId());
        vo.setPlatform(entity.getPlatform());
        vo.setButtonText(entity.getButtonText());
        vo.setJumpType(entity.getJumpType());
        vo.setJumpTarget(entity.getJumpTarget());
        return vo;
    }

    public TaskStepPlatform toEntity() {
        TaskStepPlatform entity = new TaskStepPlatform();
        entity.setId(this.id);
        entity.setStepId(this.stepId);
        entity.setPlatform(this.platform);
        entity.setButtonText(this.buttonText);
        entity.setJumpType(this.jumpType);
        entity.setJumpTarget(this.jumpTarget);
        return entity;
    }
}
