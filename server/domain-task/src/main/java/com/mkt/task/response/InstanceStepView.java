package com.mkt.task.response;

public record InstanceStepView(
        String stepCode,
        String name,
        String type,
        String status,
        int progressCurrent,
        Integer progressTarget) {}
