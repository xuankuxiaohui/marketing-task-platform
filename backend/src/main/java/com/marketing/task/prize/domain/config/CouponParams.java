package com.marketing.task.prize.domain.config;

import lombok.Data;

@Data
public class CouponParams implements PrizeParams {
    private String templateId;
    private int amount;
    private int expireDays;
}
