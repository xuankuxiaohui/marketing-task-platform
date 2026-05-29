package com.marketing.task.activity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisplayRuleUpdateRequest {
    @NotBlank
    private String content;
}
