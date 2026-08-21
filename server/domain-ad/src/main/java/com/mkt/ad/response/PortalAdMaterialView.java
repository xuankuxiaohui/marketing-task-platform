package com.mkt.ad.response;

import java.util.Map;

public record PortalAdMaterialView(
        long materialId,
        String trackId,
        String title,
        String subtitle,
        String imageUrl,
        String jumpType,
        Map<String, Object> jumpParams,
        int weight) {}
