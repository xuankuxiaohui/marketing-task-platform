package com.mkt.ad.domain;

import java.util.regex.Pattern;

public final class AdCodes {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{4,64}$");

    private AdCodes() {}

    public static boolean valid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }
}
