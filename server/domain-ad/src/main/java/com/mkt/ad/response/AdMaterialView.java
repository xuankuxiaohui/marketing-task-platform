package com.mkt.ad.response;

import java.time.Instant;
import java.util.Map;

public record AdMaterialView(
        long id,
        String title,
        String subtitle,
        String imageUrl,
        String jumpType,
        Map<String, Object> jumpParams,
        int weight,
        Instant startTime,
        Instant endTime,
        String status) {}
