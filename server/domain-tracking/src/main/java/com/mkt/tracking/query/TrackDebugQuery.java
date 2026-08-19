package com.mkt.tracking.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record TrackDebugQuery(
        String eventCode,
        Long userId,
        String source,
        String deviceId,
        Instant from,
        Instant to,
        PageQuery page) {

    public TrackDebugQuery {
        page = page == null ? PageQuery.of(null, null) : page;
    }
}
