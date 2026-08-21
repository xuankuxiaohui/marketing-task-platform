package com.mkt.ad.response;

import java.util.List;

public record AdPositionView(
        long id,
        String code,
        String name,
        String form,
        List<String> platforms,
        String status,
        List<AdPlacementView> placements,
        int overlapCount) {}
