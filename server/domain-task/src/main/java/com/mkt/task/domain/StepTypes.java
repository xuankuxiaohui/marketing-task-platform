package com.mkt.task.domain;

import java.util.Set;

public final class StepTypes {

    public static final String PASSIVE = "PASSIVE";
    public static final String CLICK = "CLICK";
    public static final String CALLBACK = "CALLBACK";
    public static final String PROGRESS = "PROGRESS";
    public static final String REWARD = "REWARD";

    private static final Set<String> ALL = Set.of(PASSIVE, CLICK, CALLBACK, PROGRESS, REWARD);

    private StepTypes() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
