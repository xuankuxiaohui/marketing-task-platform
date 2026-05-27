package com.marketing.task.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchTaskResult {
    private List<Long> success;
    private List<FailedItem> failed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private Long id;
        private String reason;
    }
}
