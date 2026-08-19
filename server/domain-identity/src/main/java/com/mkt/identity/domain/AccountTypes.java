package com.mkt.identity.domain;

/** Session accountType values (R6.1): admin = 后台, portal = 门户. */
public final class AccountTypes {

    public static final String ADMIN = "admin";
    public static final String PORTAL = "portal";

    private AccountTypes() {}

    public static boolean valid(String accountType) {
        return ADMIN.equals(accountType) || PORTAL.equals(accountType);
    }
}
