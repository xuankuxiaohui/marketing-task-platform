package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_platform")
public class TaskPlatform {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String platform;
    private String flowDesc;
    private String buttonText;
    private String jumpUri;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
