package com.mkt.ad.domain;

import java.util.Locale;

public final class AdGrayTypes {

    public static final String NONE = "NONE";
    public static final String RATIO = "RATIO";

    private AdGrayTypes() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean valid(String value) {
        String normalized = normalize(value);
        return NONE.equals(normalized) || RATIO.equals(normalized);
    }
}
