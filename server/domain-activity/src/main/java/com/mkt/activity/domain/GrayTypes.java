package com.mkt.activity.domain;

import java.util.Set;

public final class GrayTypes {

    public static final String NONE = "NONE";
    public static final String RATIO = "RATIO";

    private static final Set<String> ALL = Set.of(NONE, RATIO);

    private GrayTypes() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }
}
