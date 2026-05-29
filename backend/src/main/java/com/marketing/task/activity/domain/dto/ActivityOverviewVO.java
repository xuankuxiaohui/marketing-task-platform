package com.marketing.task.activity.domain.dto;

import lombok.Data;

@Data
public class ActivityOverviewVO {
    private String activityCode;
    private String activityName;
    private long participantCount;
    private long completionCount;
    private long rewardCount;
}
