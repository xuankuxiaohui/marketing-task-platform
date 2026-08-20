package com.mkt.task.response;

import java.time.Instant;

public record AdminInstanceStepView(
        String stepCode,
        String type,
        String status,
        int progressCurrent,
        Integer progressTarget,
        Instant activatedAt,
        Instant completedAt) {}
