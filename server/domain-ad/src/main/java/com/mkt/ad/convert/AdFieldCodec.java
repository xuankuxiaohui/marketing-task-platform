package com.mkt.ad.convert;

import com.mkt.kernel.json.JsonUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

public final class AdFieldCodec {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private AdFieldCodec() {}

    public static String json(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        return JsonUtil.toJson(value);
    }

    public static List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> parsed = JsonUtil.fromJson(json, STRINGS);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    public static Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = JsonUtil.fromJson(json, MAP);
        return parsed == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parsed));
    }
}
