package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CallbackRequest {
    private Long instanceId;
    private String userId;
    private Long taskId;
    private String cycleKey;

    @NotBlank
    private String callbackEventKey;
}
