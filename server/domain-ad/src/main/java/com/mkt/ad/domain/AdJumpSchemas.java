package com.mkt.ad.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Jump params reuse R11.10 platform-action schema (R30.2). */
public final class AdJumpSchemas {

    public static final Set<String> TYPES = Set.of("NONE", "ROUTE", "LINK", "SCHEME");

    private AdJumpSchemas() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "NONE";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean validType(String value) {
        return TYPES.contains(normalize(value));
    }

    public static boolean validParams(String jumpType, Map<String, Object> params) {
        String type = normalize(jumpType);
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
        return url.toLowerCase(Locale.ROOT).startsWith("https://");
    }
}
