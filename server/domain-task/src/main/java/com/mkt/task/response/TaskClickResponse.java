package com.mkt.task.response;

import java.util.List;

public record TaskClickResponse(
        long instanceId,
        String stepStatus,
        String instanceStatus,
        CurrentStepView nextStep,
        List<RewardFeedbackView> rewardFeedback) {}
