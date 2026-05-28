package com.marketing.task.activity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("activity_stats")
public class ActivityStats {
    private Long activityId;
    private LocalDate statDate;
    private Integer participantCount;
    private Integer completionCount;
    private Integer rewardCount;
}
