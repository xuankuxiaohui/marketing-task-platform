package com.marketing.task.activity.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleCheckResult {
    private boolean passed;
    private String failCode;
    private String failMessage;
    private String checkerType;

    public static RuleCheckResult pass() {
        return new RuleCheckResult(true, null, null, null);
    }

    public static RuleCheckResult fail(String failCode, String failMessage, String checkerType) {
        return new RuleCheckResult(false, failCode, failMessage, checkerType);
    }
}
