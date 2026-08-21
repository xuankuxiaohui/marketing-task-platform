package com.mkt.activity.response;

import java.time.Instant;
import java.util.List;

public record ActivityView(
        long id,
        String code,
        String name,
        Instant startTime,
        Instant endTime,
        String status,
        int version,
        boolean pendingRevision,
        Instant schedulePublishAt,
        Instant scheduleOfflineAt,
        String richText,
        String contentHash,
        ActivityGrayView gray,
        List<SubmoduleView> submodules,
        Long participationPrizeId,
        List<Long> allowUserIds,
        List<String> allowCrowdCodes,
        boolean newUserOnly,
        int newUserDays,
        Integer userDailyLimit,
        Integer userTotalLimit,
        Integer globalDailyLimit,
        List<String> regions) {}
