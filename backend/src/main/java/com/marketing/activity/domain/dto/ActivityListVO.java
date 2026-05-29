package com.marketing.activity.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityListVO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean hasDisplayRule;
}
