package com.marketing.task.prize.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class GrantResult {
    private boolean success;
    private String tradeNo;
    private String errorMessage;

    public static GrantResult success(String tradeNo) {
        return of(true, tradeNo, null);
    }

    public static GrantResult fail(String errorMessage) {
        return of(false, null, errorMessage);
    }
}
