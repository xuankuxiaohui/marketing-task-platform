package com.mkt.ad.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Catalog cached under ad:position:{code}. Schedule/freq applied at serve time. */
public record CachedAdSnapshot(CachedPosition position, List<CachedPlacement> placements) {

    public record CachedPosition(
            long id, String code, String name, String form, List<String> platforms, String status) {}

    public record CachedPlacement(
            long id,
            long materialId,
            int weight,
            Instant startTime,
            Instant endTime,
            List<String> platforms,
            String grayType,
            Integer grayRatio,
            Long crowdId,
            String status,
            CachedMaterial material) {}

    public record CachedMaterial(
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
}
