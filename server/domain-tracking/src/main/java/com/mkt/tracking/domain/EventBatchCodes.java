package com.mkt.tracking.domain;

import com.mkt.kernel.json.JsonUtil;
import tools.jackson.databind.JsonNode;

/** Row-level {@code event_code} is the first accepted event; query expands the JSON batch (R29.3). */
public final class EventBatchCodes {

    private EventBatchCodes() {}

    public static boolean matches(String rowEventCode, String eventsJson, String eventCode) {
        if (eventCode == null || eventCode.isBlank()) {
            return true;
        }
        if (eventCode.equals(rowEventCode)) {
            return true;
        }
        if (eventsJson == null || eventsJson.isBlank()) {
            return false;
        }
        try {
            JsonNode arr = JsonUtil.readTree(eventsJson);
            if (arr == null || !arr.isArray()) {
                return false;
            }
            for (JsonNode item : arr) {
                JsonNode code = item.get("code");
                if (code != null && eventCode.equals(code.asString())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
