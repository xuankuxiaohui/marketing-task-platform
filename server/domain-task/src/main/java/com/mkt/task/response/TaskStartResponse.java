package com.mkt.task.response;

public record TaskStartResponse(long instanceId, String instanceStatus, CurrentStepView currentStep) {}
