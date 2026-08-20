package com.mkt.reward.convert;

import com.mkt.kernel.json.JsonUtil;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

public final class RewardJson {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private RewardJson() {}

    public static String map(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        return JsonUtil.toJson(values);
    }

    public static Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> values = JsonUtil.fromJson(json, OBJECT_MAP);
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public static String strings(List<String> values) {
        if (values == null) {
            return null;
        }
        return JsonUtil.toJson(values);
    }

    public static List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> values = JsonUtil.fromJson(json, STRING_LIST);
        return values == null ? List.of() : List.copyOf(values);
    }
}
