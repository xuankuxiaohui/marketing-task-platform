package com.marketing.task.prize.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ClaimResult {
    private String status;
    private String tradeNo;
    private String errorMessage;

    public static ClaimResult granted(String tradeNo) {
        return ClaimResult.builder().status("GRANTED").tradeNo(tradeNo).build();
    }

    public static ClaimResult failed(String errorMessage) {
        return ClaimResult.builder().status("FAILED").errorMessage(errorMessage).build();
    }

    public static ClaimResult inProgress() {
        return ClaimResult.builder().status("CLAIMING").build();
    }
}
