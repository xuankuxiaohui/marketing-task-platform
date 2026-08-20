package com.mkt.reward.domain;

import java.util.regex.Pattern;

/** Prize code: 4–64 {@code [a-z0-9_-]} (R17.1). */
public final class PrizeCodes {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{4,64}$");

    private PrizeCodes() {}

    public static boolean valid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }
}
