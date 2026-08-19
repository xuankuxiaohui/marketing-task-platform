package com.mkt.task.domain;

import java.util.Set;

public final class CrowdStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private static final Set<String> ALL = Set.of(ENABLED, DISABLED);

    private CrowdStatuses() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
