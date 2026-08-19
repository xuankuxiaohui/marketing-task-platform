package com.mkt.tracking.convert;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.tracking.command.TrackMetadataSaveCommand;
import com.mkt.tracking.domain.PropSchemaItem;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.response.TrackMetadataResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import tools.jackson.core.type.TypeReference;

public final class TrackMetadataConvert {

    private static final TypeReference<List<PropSchemaItem>> SCHEMA_TYPE = new TypeReference<>() {};

    private TrackMetadataConvert() {}

    public static EvtEventMetadataEntity toEntity(TrackMetadataSaveCommand command, Clock clock) {
        EvtEventMetadataEntity entity = new EvtEventMetadataEntity();
        apply(command, entity);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public static void apply(TrackMetadataSaveCommand command, EvtEventMetadataEntity entity) {
        entity.setEventCode(command.eventCode());
        entity.setName(command.name());
        entity.setPropSchema(serializeSchema(command.propSchema()));
        entity.setStatus(command.status());
        entity.setOwner(blankToNull(command.owner()));
        entity.setRemark(blankToNull(command.remark()));
    }

    public static TrackMetadataResponse toResponse(EvtEventMetadataEntity entity) {
        return new TrackMetadataResponse(
                entity.getId(),
                entity.getEventCode(),
                entity.getName(),
                parseSchema(entity.getPropSchema()),
                entity.getStatus(),
                entity.getOwner(),
                entity.getRemark(),
                TrackTime.toInstant(entity.getCreatedAt()),
                TrackTime.toInstant(entity.getUpdatedAt()));
    }

    static String serializeSchema(List<PropSchemaItem> schema) {
        if (schema == null) {
            return null;
        }
        return JsonUtil.toJson(schema);
    }

    static List<PropSchemaItem> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<PropSchemaItem> parsed = JsonUtil.fromJson(json, SCHEMA_TYPE);
        return parsed == null ? List.of() : parsed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
