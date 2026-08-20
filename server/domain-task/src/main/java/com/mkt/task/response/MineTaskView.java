package com.mkt.task.response;

import java.time.Instant;

public record MineTaskView(
        long instanceId,
        long taskId,
        String taskName,
        String iconUrl,
        String category,
        String status,
        String currentStepName,
        Instant startedAt) {}
