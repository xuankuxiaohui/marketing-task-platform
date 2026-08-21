package com.mkt.reward.domain;

import java.util.regex.Pattern;

/** Prize category code: 4–32 {@code [A-Z0-9_]} (design §3.4.1). */
public final class CategoryCodes {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9_]{4,32}$");

    private CategoryCodes() {}

    public static boolean valid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }
}
