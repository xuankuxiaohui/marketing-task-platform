package com.mkt.task.domain;

import java.util.Set;

public final class DefinitionStatuses {

    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String OFFLINE = "OFFLINE";

    private static final Set<String> ALL = Set.of(DRAFT, SCHEDULED, PUBLISHED, OFFLINE);

    private DefinitionStatuses() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }

    public static boolean publishedFamily(String value) {
        return PUBLISHED.equals(value) || SCHEDULED.equals(value);
    }

    public static boolean deletable(String value) {
        return DRAFT.equals(value);
    }
}
