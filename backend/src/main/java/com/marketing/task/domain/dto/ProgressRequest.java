package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProgressRequest {
    private Long instanceId;
    private String userId;
    private Long taskId;
    private String cycleKey;

    @NotNull
    private Long stepId;

    @NotNull
    private Integer progressValue;
}
