package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskStepTransition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "步骤分支转换 VO")
public class TaskStepTransitionVO {
    private Long id;
    @Schema(description = "源步骤编码 (用于聚合保存时的 code→id 解析)")
    private String stepCode;
    @Schema(description = "目标步骤编码 (用于聚合保存时的 code→id 解析)")
    private String targetStepCode;
    private String conditionExpr;
    private Integer priority;
    private String description;

    public static TaskStepTransitionVO from(TaskStepTransition entity) {
        if (entity == null) return null;
        TaskStepTransitionVO vo = new TaskStepTransitionVO();
        vo.setId(entity.getId());
        vo.setConditionExpr(entity.getConditionExpr());
        vo.setPriority(entity.getPriority());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    public TaskStepTransition toEntity() {
        TaskStepTransition entity = new TaskStepTransition();
        entity.setId(this.id);
        entity.setConditionExpr(this.conditionExpr);
        entity.setPriority(this.priority != null ? this.priority : 0);
        entity.setDescription(this.description);
        return entity;
    }
}
