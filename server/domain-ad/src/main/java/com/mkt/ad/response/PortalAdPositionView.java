package com.mkt.ad.response;

import java.util.List;

public record PortalAdPositionView(
        String code,
        String form,
        List<PortalAdMaterialView> materials,
        Integer splashDurationSeconds,
        Integer carouselIntervalSeconds) {}
