package com.mkt.identity.support;

/** Appendix B identity permission codes used by RBAC endpoints (design §4.2). */
public final class IdentityPermissions {

    public static final String ROLE_QUERY = "identity:role:query";
    public static final String ROLE_CREATE = "identity:role:create";
    public static final String ROLE_UPDATE = "identity:role:update";
    public static final String ROLE_DELETE = "identity:role:delete";
    public static final String ROLE_ASSIGN_PERMISSION = "identity:role:assign-permission";
    public static final String PERMISSION_QUERY = "identity:permission:query";
    public static final String PERMISSION_CREATE = "identity:permission:create";
    public static final String PERMISSION_UPDATE = "identity:permission:update";
    public static final String PERMISSION_DELETE = "identity:permission:delete";

    private IdentityPermissions() {}
}
