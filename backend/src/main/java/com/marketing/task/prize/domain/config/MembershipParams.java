package com.marketing.task.prize.domain.config;

import lombok.Data;

@Data
public class MembershipParams implements PrizeParams {
    private int level;
    private int durationDays;
}
