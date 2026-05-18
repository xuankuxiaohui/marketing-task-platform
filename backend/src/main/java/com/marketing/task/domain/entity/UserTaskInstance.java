package com.marketing.task.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_task_instance")
public class UserTaskInstance {
    private Long id;
    private String userId;
    private Long taskId;
    private Integer taskVersion;
    private String cycleKey;
    private String status;
    private Integer currentStepSeq;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime rewardTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
