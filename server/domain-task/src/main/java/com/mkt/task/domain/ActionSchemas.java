package com.mkt.task.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Closed platform-action schema (R11.10). */
public final class ActionSchemas {

    public static final Set<String> PLATFORMS = Set.of("WEB", "ANDROID", "IOS", "MINIAPP", "SIMULATOR");
    public static final Set<String> TYPES = Set.of("NONE", "ROUTE", "LINK", "SCHEME");

    private ActionSchemas() {}

    public static boolean validPlatform(String value) {
        return value != null && PLATFORMS.contains(value);
    }

    public static boolean validType(String value) {
        return value != null && TYPES.contains(value);
    }

    public static boolean validParams(String actionType, Map<String, Object> params, String buttonText) {
        if (buttonText != null && buttonText.length() > 16) {
            return false;
        }
        String type = actionType == null ? "" : actionType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "NONE" -> true;
            case "ROUTE" -> text(params, "route", 64);
            case "LINK" -> httpsUrl(params);
            case "SCHEME" -> text(params, "scheme", 128);
            default -> false;
        };
    }

    private static boolean text(Map<String, Object> params, String key, int max) {
        if (params == null) {
            return false;
        }
        Object value = params.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > max) {
            return false;
        }
        return true;
    }

    private static boolean httpsUrl(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        Object value = params.get("url");
        if (!(value instanceof String url) || url.length() > 512) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://");
    }
}
