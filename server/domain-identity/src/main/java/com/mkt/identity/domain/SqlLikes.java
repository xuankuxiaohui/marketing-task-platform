package com.mkt.identity.domain;

/** Escape user input for SQL {@code LIKE} with {@code ESCAPE '\\'}. */
public final class SqlLikes {

    private SqlLikes() {}

    public static String containsOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
