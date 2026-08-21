package com.mkt.activity.domain;

import java.util.Set;

public final class SubmoduleTypes {

    public static final String TASK = "TASK";
    public static final String PRIZE_GROUP = "PRIZE_GROUP";
    public static final String SIGNIN = "SIGNIN";

    private static final Set<String> ALL = Set.of(TASK, PRIZE_GROUP, SIGNIN);

    private SubmoduleTypes() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
