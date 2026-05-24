package com.marketing.task.prize.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrantContext {
    private Long instanceId;
    private Long taskId;
    private Long stepId;
    private String cycleKey;
    private String province;
    private String role;
    private String tags;
    private String orgId;
    private Integer level;

    public String getIdempotentKey(Long prizeId) {
        return instanceId + ":" + stepId + ":" + prizeId;
    }
}
