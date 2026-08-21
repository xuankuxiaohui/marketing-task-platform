package com.mkt.ad.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdCandidate(
        long materialId,
        int weight,
        String title,
        String subtitle,
        String imageUrl,
        String jumpType,
        Map<String, Object> jumpParams,
        Instant materialStart,
        Instant materialEnd,
        Instant placementStart,
        Instant placementEnd,
        List<String> platforms,
        String grayType,
        Integer grayRatio,
        Long crowdId) {}
