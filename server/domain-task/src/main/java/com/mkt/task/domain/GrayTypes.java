package com.mkt.task.domain;

import java.util.Set;

public final class GrayTypes {

    public static final String NONE = "NONE";
    public static final String RATIO = "RATIO";
    public static final String AB = "AB";
    public static final String CROWD = "CROWD";

    private static final Set<String> ALL = Set.of(NONE, RATIO, AB, CROWD);

    private GrayTypes() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
