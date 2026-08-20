package com.mkt.task.response;

import java.time.Instant;

public record ScheduleFailureView(
        long id, Long taskId, String taskCode, String reason, Instant createdAt) {}
