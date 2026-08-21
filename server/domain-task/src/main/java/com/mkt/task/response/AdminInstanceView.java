package com.mkt.task.response;

import java.time.Instant;

public record AdminInstanceView(
        long id,
        long taskId,
        String taskCode,
        long userId,
        int version,
        String cycleKey,
        String status,
        String abandonSource,
        Instant abandonedAt,
        Instant expireAt,
        Instant startedAt,
        Instant completedAt,
        Integer costSeconds,
        int simulated) {}
