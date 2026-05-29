package com.marketing.activity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityCreateRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String description;
    private String grayType;
    private String grayConfig;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private String participationRules;
}
