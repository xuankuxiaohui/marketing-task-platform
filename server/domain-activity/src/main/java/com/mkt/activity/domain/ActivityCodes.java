package com.mkt.activity.domain;

import java.util.regex.Pattern;

public final class ActivityCodes {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{4,64}$");

    private ActivityCodes() {}

    public static boolean valid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }
}
