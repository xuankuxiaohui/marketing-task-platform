package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_step")
public class TaskStep {
    @TableId(type = IdType.AUTO)
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
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
