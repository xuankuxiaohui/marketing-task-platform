package com.mkt.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Role {@code code}: 3–30 {@code [a-z0-9_-]} (R2.1). */
public final class RoleCodes {

    public static final String SUPER_ADMIN = "super-admin";
    private static final Pattern CODE = Pattern.compile("[a-z0-9_-]{3,30}");

    private RoleCodes() {}

    public static String normalizeOrNull(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}
