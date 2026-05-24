package com.marketing.task.prize.domain.config;

import lombok.Data;

@Data
public class BadgeParams implements PrizeParams {
    private String badgeId;
    private String name;
    private String iconUrl;
}
