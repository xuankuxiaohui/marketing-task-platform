package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** Portal orgId: ≤64 {@code [A-Za-z0-9_-]} (R5.3). */
public final class OrgIds {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private OrgIds() {}

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
