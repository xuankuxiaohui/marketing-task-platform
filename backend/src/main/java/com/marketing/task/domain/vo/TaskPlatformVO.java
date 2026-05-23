package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "端配置 VO")
public class TaskPlatformVO {
    private Long id;
    private Long taskId;
    private String platform;
    private String flowDesc;
    private String buttonText;
    private String jumpUri;
    private Boolean enabled;

    public static TaskPlatformVO from(TaskPlatform entity) {
        if (entity == null) return null;
        TaskPlatformVO vo = new TaskPlatformVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setPlatform(entity.getPlatform());
        vo.setFlowDesc(entity.getFlowDesc());
        vo.setButtonText(entity.getButtonText());
        vo.setJumpUri(entity.getJumpUri());
        vo.setEnabled(entity.getEnabled());
        return vo;
    }

    public TaskPlatform toEntity() {
        TaskPlatform entity = new TaskPlatform();
        entity.setId(this.id);
        entity.setTaskId(this.taskId);
        entity.setPlatform(this.platform);
        entity.setFlowDesc(this.flowDesc);
        entity.setButtonText(this.buttonText);
        entity.setJumpUri(this.jumpUri);
        entity.setEnabled(this.enabled);
        return entity;
    }
}
