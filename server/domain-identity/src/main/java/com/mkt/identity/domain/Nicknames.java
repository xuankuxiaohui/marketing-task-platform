package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** R4.10: 1–30 CJK/latin/digit/underscore; not unique. */
public final class Nicknames {

    private static final Pattern PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9_]{1,30}$");

    private Nicknames() {}

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim();
    }

    public static boolean valid(String nickname) {
        return nickname != null && PATTERN.matcher(nickname).matches();
    }
}
