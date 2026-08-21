package com.mkt.activity.convert;

import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySubmoduleCommand;
import com.mkt.kernel.json.JsonUtil;
import java.util.List;
import tools.jackson.core.type.TypeReference;

public final class ActivityFieldCodec {

    private static final TypeReference<List<ActivitySubmoduleCommand>> SUBMODULES =
            new TypeReference<>() {};
    private static final TypeReference<List<Long>> LONGS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private ActivityFieldCodec() {}

    public static String json(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        return JsonUtil.toJson(value);
    }

    public static List<ActivitySubmoduleCommand> submodules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<ActivitySubmoduleCommand> parsed = JsonUtil.fromJson(json, SUBMODULES);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    public static List<Long> longs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Long> parsed = JsonUtil.fromJson(json, LONGS);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    public static List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> parsed = JsonUtil.fromJson(json, STRINGS);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    public static ActivityGrayCommand gray(String type, Integer ratio) {
        return new ActivityGrayCommand(type == null ? "NONE" : type, ratio);
    }

    public static ActivityDraftContent draft(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtil.fromJson(json, ActivityDraftContent.class);
    }
}
