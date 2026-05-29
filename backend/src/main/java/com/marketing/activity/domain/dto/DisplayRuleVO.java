package com.marketing.activity.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DisplayRuleVO {
    private Long id;
    private String activityCode;
    private String content;
    private String contentHash;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
