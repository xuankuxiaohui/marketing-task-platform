package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_step_platform")
public class TaskStepPlatform {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stepId;
    private String platform;
    private String buttonText;
    private String jumpType;
    private String jumpTarget;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
