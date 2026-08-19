package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** R1 / R4 username: 4–30 [a-z0-9_], stored lowercase. */
public final class Usernames {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_]{4,30}$");

    private Usernames() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    public static boolean valid(String username) {
        return username != null && PATTERN.matcher(username).matches();
    }

    public static String requireValid(String raw) {
        String normalized = normalize(raw);
        if (!valid(normalized)) {
            return null;
        }
        return normalized;
    }
}
