package com.marketing.task.prize.domain.config;

import lombok.Data;

@Data
public class PointParams implements PrizeParams {
    private int amount;
    private String reason;
}
