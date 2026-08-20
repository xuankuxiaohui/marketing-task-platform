package com.mkt.task.domain;

import java.util.Set;

public final class CycleTypes {

    public static final String NONE = "NONE";
    public static final String DAILY = "DAILY";
    public static final String MONTHLY = "MONTHLY";
    public static final String CRON = "CRON";
    public static final String SPECIAL = "SPECIAL";

    private static final Set<String> ALL = Set.of(NONE, DAILY, MONTHLY, CRON, SPECIAL);

    private CycleTypes() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
