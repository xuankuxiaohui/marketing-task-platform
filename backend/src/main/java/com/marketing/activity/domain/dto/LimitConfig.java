package com.marketing.activity.domain.dto;

import lombok.Data;

@Data
public class LimitConfig {
    private String scope;
    private int max;
}
