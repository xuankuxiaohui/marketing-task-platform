package com.mkt.ad.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Client platforms (R16.4). Missing / illegal request header becomes WEB. */
public final class AdPlatforms {

    public static final String WEB = "WEB";
    public static final String ANDROID = "ANDROID";
    public static final String IOS = "IOS";
    public static final String MINIAPP = "MINIAPP";

    private static final Set<String> ALL = Set.of(WEB, ANDROID, IOS, MINIAPP);

    private AdPlatforms() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return valid(value) ? value : WEB;
    }

    public static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(WEB);
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (valid(normalized) && !out.contains(normalized)) {
                out.add(normalized);
            }
        }
        return out.isEmpty() ? List.of(WEB) : List.copyOf(out);
    }

    public static boolean allows(List<String> allowed, String platform) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(platform);
    }
}
