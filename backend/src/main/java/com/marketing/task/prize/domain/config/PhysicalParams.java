package com.marketing.task.prize.domain.config;

import lombok.Data;

@Data
public class PhysicalParams implements PrizeParams {
    private String skuId;
    private String name;
    private boolean requireAddress;
}
