package com.mkt.task.response;

public record TaskCallbackResponse(long instanceId, String stepCode, String stepStatus, String instanceStatus) {}
