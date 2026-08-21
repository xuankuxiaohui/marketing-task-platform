package com.mkt.ad.domain;

import java.util.Locale;
import java.util.Set;

public final class AdStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private static final Set<String> ALL = Set.of(ENABLED, DISABLED);

    private AdStatuses() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return ENABLED;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
