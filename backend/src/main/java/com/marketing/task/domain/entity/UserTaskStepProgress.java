package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_task_step_progress")
public class UserTaskStepProgress {
    private Long id;
    private Long instanceId;
    private Long stepId;
    private String status;
    private Integer progressValue;
    private LocalDateTime completeTime;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
