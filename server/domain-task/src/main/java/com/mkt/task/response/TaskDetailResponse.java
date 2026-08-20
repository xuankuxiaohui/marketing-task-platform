package com.mkt.task.response;

import java.util.List;

public record TaskDetailResponse(
        String status,
        Long instanceId,
        TaskBriefView task,
        List<StepPreviewView> stepsPreview,
        RewardPreviewView rewardPreview,
        List<InstanceStepView> steps,
        CurrentStepView currentStep) {}
