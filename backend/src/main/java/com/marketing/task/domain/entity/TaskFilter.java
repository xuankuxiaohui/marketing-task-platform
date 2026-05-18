package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_filter")
public class TaskFilter {
    private Long id;
    private Long taskId;
    private Integer seq;
    private String expression;
    private String logicOp;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
