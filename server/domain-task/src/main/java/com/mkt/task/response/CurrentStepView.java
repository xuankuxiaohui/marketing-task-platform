package com.mkt.task.response;

public record CurrentStepView(
        String stepCode,
        String name,
        String type,
        Integer progressCurrent,
        Integer progressTarget,
        PlatformActionView action) {}
