package com.mkt.ad.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record AdPlacementSaveCommand(
        @NotNull Long materialId,
        @NotNull Integer weight,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        List<String> platforms,
        String grayType,
        Integer grayRatio,
        Long crowdId,
        String status) {}
