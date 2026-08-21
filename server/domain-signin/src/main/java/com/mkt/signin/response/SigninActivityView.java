package com.mkt.signin.response;

import java.time.Instant;
import java.util.List;

public record SigninActivityView(
        long id,
        String code,
        String name,
        Instant startTime,
        Instant endTime,
        String status,
        int version,
        boolean pendingRevision,
        Instant schedulePublishAt,
        List<SigninTierView> tiers) {}
