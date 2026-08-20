package com.mkt.reward.response;

public record ReconItemView(
        long id,
        long batchId,
        Long grantRecordId,
        String fulfillmentRef,
        Integer platformCostFen,
        Integer channelAmountFen,
        String result,
        String action,
        String actionRef,
        String reviewStatus,
        String fulfillFailReason,
        String effectivePolicy,
        String remark) {}
