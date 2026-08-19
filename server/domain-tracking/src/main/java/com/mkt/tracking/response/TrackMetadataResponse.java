package com.mkt.tracking.response;

import com.mkt.tracking.domain.PropSchemaItem;
import java.time.Instant;
import java.util.List;

public record TrackMetadataResponse(
        long id,
        String eventCode,
        String name,
        List<PropSchemaItem> propSchema,
        String status,
        String owner,
        String remark,
        Instant createdAt,
        Instant updatedAt) {}
