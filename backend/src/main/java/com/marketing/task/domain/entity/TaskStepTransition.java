package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_step_transition")
public class TaskStepTransition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stepId;
    private Long targetStepId;
    private String conditionExpr;
    private Integer priority;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
