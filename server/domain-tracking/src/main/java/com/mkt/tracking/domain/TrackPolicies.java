package com.mkt.tracking.domain;

import com.mkt.kernel.json.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure ingest filters (R28.5 / R28.6 / R29.2). */
public final class TrackPolicies {

    private TrackPolicies() {
    }

    public static EventFilterDecision decide(
            String code,
            Object props,
            int maxPayloadBytes,
            MetadataStatus metadata,
            UnregisteredPolicy unregisteredPolicy,
            DisabledEventPolicy disabledPolicy) {
        if (!EventCodeSyntax.valid(code)) {
            return EventFilterDecision.DROP_MALFORMED;
        }
        if (payloadBytes(props) > maxPayloadBytes) {
            return EventFilterDecision.DROP_MALFORMED;
        }
        if (metadata == MetadataStatus.MISSING) {
            return unregisteredPolicy == UnregisteredPolicy.REJECT
                    ? EventFilterDecision.DROP_UNREGISTERED
                    : EventFilterDecision.ACCEPT;
        }
        if (metadata == MetadataStatus.DISABLED) {
            return disabledPolicy == DisabledEventPolicy.DROP_COUNT
                    ? EventFilterDecision.DROP_DISABLED
                    : EventFilterDecision.ACCEPT;
        }
        return EventFilterDecision.ACCEPT;
    }

    public static Map<String, Object> normalizeProps(Object props) {
        if (props == null) {
            return Map.of();
        }
        if (props instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }
        return Map.of("value", props);
    }

    public static int payloadBytes(Object props) {
        return JsonUtil.toJson(normalizeProps(props)).getBytes(StandardCharsets.UTF_8).length;
    }
}
