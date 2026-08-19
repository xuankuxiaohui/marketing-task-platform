package com.mkt.tracking.response;

import java.time.Instant;
import java.util.List;

public record TrackDebugEventResponse(
        long id,
        String source,
        String eventCode,
        Long userId,
        String deviceId,
        String platform,
        String appVersion,
        String ip,
        List<TrackDebugEventItem> events,
        int batchSize,
        boolean registered,
        boolean simulated,
        Instant serverTime) {}
