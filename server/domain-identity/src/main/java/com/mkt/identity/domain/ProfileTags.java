package com.mkt.identity.domain;

import java.util.ArrayList;
import java.util.List;

/** Portal tags: at most 20 values, full-replace semantics (R5.3). */
public final class ProfileTags {

    public static final int MAX = 20;

    private ProfileTags() {}

    public static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String tag : raw) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            out.add(tag.trim());
        }
        return List.copyOf(out);
    }

    public static boolean withinLimit(List<String> tags) {
        return tags == null || tags.size() <= MAX;
    }
}
