package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "步骤 VO")
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
    private String flowDesc;

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
        vo.setFlowDesc(step.getFlowDesc());
        return vo;
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
        step.setFlowDesc(this.flowDesc);
        return step;
    }
}
