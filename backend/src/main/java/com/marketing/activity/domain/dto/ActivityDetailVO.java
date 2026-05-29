package com.marketing.activity.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityDetailVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private String grayType;
    private String grayConfig;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String participationRules;
    private boolean hasDisplayRule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
