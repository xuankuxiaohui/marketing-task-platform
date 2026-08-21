package com.mkt.activity.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record ActivitySaveCommand(
        Long id,
        @NotBlank @Size(min = 4, max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull String richText,
        ActivityGrayCommand gray,
        @Valid List<ActivitySubmoduleCommand> submodules,
        Long participationPrizeId,
        List<Long> allowUserIds,
        List<String> allowCrowdCodes,
        Boolean newUserOnly,
        Integer newUserDays,
        Integer userDailyLimit,
        Integer userTotalLimit,
        Integer globalDailyLimit,
        List<String> regions) {}
