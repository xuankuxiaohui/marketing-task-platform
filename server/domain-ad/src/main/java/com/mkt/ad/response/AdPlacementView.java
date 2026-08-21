package com.mkt.ad.response;

import java.time.Instant;
import java.util.List;

public record AdPlacementView(
        long id,
        long positionId,
        long materialId,
        int weight,
        Instant startTime,
        Instant endTime,
        List<String> platforms,
        String grayType,
        Integer grayRatio,
        Long crowdId,
        String status,
        AdMaterialView material) {}
