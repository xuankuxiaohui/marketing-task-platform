package com.mkt.task.domain;

import java.util.Set;

public final class InstanceStatuses {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String ABANDONED = "ABANDONED";
    public static final String EXPIRED = "EXPIRED";
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String OFFLINE = "OFFLINE";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, ABANDONED, EXPIRED);

    private InstanceStatuses() {}

    public static boolean terminal(String value) {
        return value != null && TERMINAL.contains(value);
    }
}
