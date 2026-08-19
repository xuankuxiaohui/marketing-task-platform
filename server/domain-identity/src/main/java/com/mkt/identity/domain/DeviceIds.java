package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** R4.7 UUID v4; illegal/missing → treat as absent. */
public final class DeviceIds {

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private DeviceIds() {
    }

    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return UUID_V4.matcher(value).matches() ? value : null;
    }
}
