package com.marketing.task.domain.vo;

import com.marketing.task.domain.entity.TaskFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "过滤器 VO")
public class TaskFilterVO {
    private Long id;
    private Long taskId;
    private Integer seq;
    private String expression;
    private String logicOp;
    private String description;
    private Boolean enabled;

    public static TaskFilterVO from(TaskFilter filter) {
        if (filter == null) return null;
        TaskFilterVO vo = new TaskFilterVO();
        vo.setId(filter.getId());
        vo.setTaskId(filter.getTaskId());
        vo.setSeq(filter.getSeq());
        vo.setExpression(filter.getExpression());
        vo.setLogicOp(filter.getLogicOp());
        vo.setDescription(filter.getDescription());
        vo.setEnabled(filter.getEnabled());
        return vo;
    }

    public TaskFilter toEntity() {
        TaskFilter entity = new TaskFilter();
        entity.setId(this.id);
        entity.setTaskId(this.taskId);
        entity.setSeq(this.seq);
        entity.setExpression(this.expression);
        entity.setLogicOp(this.logicOp);
        entity.setDescription(this.description);
        entity.setEnabled(this.enabled);
        return entity;
    }
}
