package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchTaskRequest {
    @NotEmpty
    private List<Long> taskIds;
}
