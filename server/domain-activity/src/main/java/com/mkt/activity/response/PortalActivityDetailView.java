package com.mkt.activity.response;

import java.time.Instant;
import java.util.List;

public record PortalActivityDetailView(
        long id,
        String code,
        String name,
        Instant startTime,
        Instant endTime,
        String richText,
        String contentHash,
        int version,
        List<SubmoduleView> submodules) {}
