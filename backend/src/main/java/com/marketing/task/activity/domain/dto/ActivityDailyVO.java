package com.marketing.task.activity.domain.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ActivityDailyVO {
    private String activityCode;
    private LocalDate statDate;
    private Integer participantCount;
    private Integer completionCount;
    private Integer rewardCount;
}
