package com.mkt.task.convert;

import com.mkt.kernel.json.JsonUtil;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

public final class TaskJson {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private TaskJson() {}

    public static String longs(List<Long> values) {
        if (values == null) {
            return null;
        }
        return JsonUtil.toJson(values);
    }

    public static List<Long> longs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Long> values = JsonUtil.fromJson(json, LONG_LIST);
        return values == null ? List.of() : List.copyOf(values);
    }

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
}
