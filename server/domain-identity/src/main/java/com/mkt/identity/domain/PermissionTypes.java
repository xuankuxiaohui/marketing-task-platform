package com.mkt.identity.domain;

/** sys_permission.type (design §3.2.2). */
public final class PermissionTypes {

    public static final String MENU = "MENU";
    public static final String OPERATION = "OPERATION";

    private PermissionTypes() {}

    public static boolean isMenu(String type) {
        return MENU.equals(type);
    }

    public static boolean isOperation(String type) {
        return OPERATION.equals(type);
    }

    public static boolean valid(String type) {
        return isMenu(type) || isOperation(type);
    }
}
