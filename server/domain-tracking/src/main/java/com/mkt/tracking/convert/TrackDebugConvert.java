package com.mkt.tracking.convert;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.response.TrackDebugEventItem;
import com.mkt.tracking.response.TrackDebugEventResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

public final class TrackDebugConvert {

    private TrackDebugConvert() {}

    public static TrackDebugEventResponse toResponse(EvtEventLogEntity entity) {
        return new TrackDebugEventResponse(
                entity.getId(),
                entity.getSource(),
                entity.getEventCode(),
                entity.getUserId(),
                entity.getDeviceId(),
                entity.getPlatform(),
                entity.getAppVersion(),
                entity.getIp(),
                parseEvents(entity.getEvents()),
                entity.getBatchSize() == null ? 0 : entity.getBatchSize(),
                entity.getRegistered() != null && entity.getRegistered() == 1,
                entity.getSimulated() != null && entity.getSimulated() == 1,
                TrackTime.toInstant(entity.getServerTime()));
    }

    static List<TrackDebugEventItem> parseEvents(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode arr = JsonUtil.readTree(json);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<TrackDebugEventItem> items = new ArrayList<>();
        for (JsonNode node : arr) {
            String code = text(node.get("code"));
            String clientTime = text(node.get("clientTime"));
            items.add(new TrackDebugEventItem(code, props(node.get("props")), clientTime));
        }
        return items;
    }

    private static Map<String, Object> props(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> parsed = JsonUtil.fromJson(node.toString(), new TypeReference<Map<String, Object>>() {});
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(parsed);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asString();
    }
}
