package com.marketing.activity.domain.dto;

import lombok.Data;

@Data
public class ActivitySummaryVO {
    private String activityCode;
    private long totalParticipants;
    private long totalCompletions;
    private long totalRewards;
}
