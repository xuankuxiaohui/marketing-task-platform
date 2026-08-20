package com.mkt.identity.domain;

/** Admin / portal / internal-app enablement (R3.4 / R5.2 / R15.2). */
public final class UserStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private UserStatuses() {}

    public static boolean valid(String status) {
        return ENABLED.equals(status) || DISABLED.equals(status);
    }
}
