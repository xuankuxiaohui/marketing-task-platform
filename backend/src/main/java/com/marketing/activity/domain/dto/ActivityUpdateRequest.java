package com.marketing.activity.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityUpdateRequest {
    private String name;
    private String description;
    private String grayType;
    private String grayConfig;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String participationRules;
}
