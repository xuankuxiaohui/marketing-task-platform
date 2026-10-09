package com.mkt.activity.response;

import java.time.Instant;
import java.util.List;

public record PortalActivityView(
        long id,
        String code,
        String name,
        Instant startTime,
        Instant endTime,
        List<SubmoduleView> submodules) {}
