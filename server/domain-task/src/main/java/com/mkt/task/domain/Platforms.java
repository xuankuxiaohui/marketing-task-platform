package com.mkt.task.domain;

import java.util.Set;

public final class Platforms {

    public static final String WEB = "WEB";
    public static final String ANDROID = "ANDROID";
    public static final String IOS = "IOS";
    public static final String MINIAPP = "MINIAPP";
    public static final String SIMULATOR = "SIMULATOR";

    private static final Set<String> ALL = Set.of(WEB, ANDROID, IOS, MINIAPP, SIMULATOR);

    private Platforms() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        String value = raw.trim().toUpperCase();
        return valid(value) ? value : WEB;
    }
}
