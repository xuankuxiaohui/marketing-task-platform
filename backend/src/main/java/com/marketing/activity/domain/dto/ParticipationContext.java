package com.marketing.activity.domain.dto;

import lombok.Data;

@Data
public class ParticipationContext {
    private Long userId;
    private String clientIp;
    private String deviceId;
    private Integer userDays;
}
