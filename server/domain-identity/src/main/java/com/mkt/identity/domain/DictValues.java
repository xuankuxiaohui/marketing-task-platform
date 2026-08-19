package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** Dict entry value: ≤64 {@code [A-Za-z0-9_-.]} (R7.2). */
public final class DictValues {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_\\-.]{1,64}");

    private DictValues() {}

    public static String normalizeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}
