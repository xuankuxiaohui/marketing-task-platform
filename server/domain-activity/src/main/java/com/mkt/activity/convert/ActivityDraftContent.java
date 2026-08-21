package com.mkt.activity.convert;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import java.time.Instant;
import java.util.List;

public record ActivityDraftContent(
        String name,
        Instant startTime,
        Instant endTime,
        String richText,
        ActivityGrayCommand gray,
        List<ActivitySubmoduleCommand> submodules,
        Long participationPrizeId,
        List<Long> allowUserIds,
        List<String> allowCrowdCodes,
        Boolean newUserOnly,
        Integer newUserDays,
        Integer userDailyLimit,
        Integer userTotalLimit,
        Integer globalDailyLimit,
        List<String> regions,
        Instant scheduleOfflineAt) {}
