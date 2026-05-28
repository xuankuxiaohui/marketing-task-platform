package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_display_rule")
public class ActivityDisplayRule {
    private Long activityId;
    private String content;
    private String contentHash;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
