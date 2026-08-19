package com.mkt.identity.domain;

/** Role / permission status (R2.1). */
public final class RoleStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private RoleStatuses() {}

    public static boolean valid(String status) {
        return ENABLED.equals(status) || DISABLED.equals(status);
    }
}
