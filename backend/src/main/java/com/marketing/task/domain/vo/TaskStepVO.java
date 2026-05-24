package com.marketing.task.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "步骤 VO")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStepVO {
    private Long id;
    private Long taskId;
    private Integer seq;
    private String code;
    private String name;
    private String description;
    private String type;
    private Integer targetValue;
    private String callbackEventKey;
    private String rewardConfigJson;
    private Long prizeId;
    private Integer prizeQuantity;
    private String flowDesc;
    private String extraJson;
    private String buttonText;
    private String buttonTextOriginal;
    private String jumpType;
    private String jumpTarget;

    public static TaskStepVO from(TaskStep step) {
        if (step == null) return null;
        TaskStepVO vo = new TaskStepVO();
        vo.setId(step.getId());
        vo.setTaskId(step.getTaskId());
        vo.setSeq(step.getSeq());
        vo.setCode(step.getCode());
        vo.setName(step.getName());
        vo.setDescription(step.getDescription());
        vo.setType(step.getType());
        vo.setTargetValue(step.getTargetValue());
        vo.setCallbackEventKey(step.getCallbackEventKey());
        vo.setRewardConfigJson(step.getRewardConfigJson());
        vo.setPrizeId(step.getPrizeId());
        vo.setPrizeQuantity(step.getPrizeQuantity());
        vo.setFlowDesc(step.getFlowDesc());
        vo.setExtraJson(step.getExtraJson());
        return vo;
    }

    public void applyPlatformConfig(TaskStepPlatform config) {
        if (config == null) return;
        if (config.getButtonText() != null && !config.getButtonText().isBlank()) {
            this.buttonTextOriginal = config.getButtonText();
            this.buttonText = config.getButtonText();
        }
        this.jumpType = config.getJumpType();
        this.jumpTarget = config.getJumpTarget();
    }

    public TaskStep toEntity() {
        TaskStep step = new TaskStep();
        step.setId(this.id);
        step.setTaskId(this.taskId);
        step.setSeq(this.seq);
        step.setCode(this.code);
        step.setName(this.name);
        step.setDescription(this.description);
        step.setType(this.type);
        step.setTargetValue(this.targetValue);
        step.setCallbackEventKey(this.callbackEventKey);
        step.setRewardConfigJson(this.rewardConfigJson);
        step.setPrizeId(this.prizeId);
        step.setPrizeQuantity(this.prizeQuantity);
        step.setFlowDesc(this.flowDesc);
        step.setExtraJson(this.extraJson);
        return step;
    }
}
