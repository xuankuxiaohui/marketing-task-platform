package com.marketing.task.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchTaskRequest {
    @NotEmpty
    @Size(max = 50, message = "批量操作最多支持50个任务")
    private List<Long> taskIds;
}
