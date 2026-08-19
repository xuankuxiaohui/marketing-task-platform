package com.mkt.tracking.query;

import java.time.LocalDateTime;

public record EventLogQuery(
        String eventCode,
        Long userId,
        String source,
        String deviceId,
        LocalDateTime from,
        LocalDateTime to,
        int sampleRatioPercent,
        long offset,
        int limit) {}
