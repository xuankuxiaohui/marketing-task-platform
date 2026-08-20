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
    public static final String ADMIN_USER_QUERY = "identity:admin-user:query";
    public static final String ADMIN_USER_CREATE = "identity:admin-user:create";
    public static final String ADMIN_USER_UPDATE = "identity:admin-user:update";
    public static final String ADMIN_USER_DISABLE = "identity:admin-user:disable";
    public static final String ADMIN_USER_RESET_PASSWORD = "identity:admin-user:reset-password";
    public static final String ADMIN_USER_DELETE = "identity:admin-user:delete";
    public static final String PORTAL_USER_QUERY = "identity:portal-user:query";
    public static final String PORTAL_USER_UPDATE_PROFILE = "identity:portal-user:update-profile";
    public static final String PORTAL_USER_DISABLE = "identity:portal-user:disable";
    public static final String PORTAL_USER_RESET_PASSWORD = "identity:portal-user:reset-password";
    public static final String PORTAL_USER_DELETE = "identity:portal-user:delete";
    public static final String SESSION_QUERY = "identity:session:query";
    public static final String SESSION_KICK = "identity:session:kick";
    public static final String INTERNAL_APP_QUERY = "identity:internal-app:query";
    public static final String INTERNAL_APP_ADD = "identity:internal-app:add";
    public static final String INTERNAL_APP_EDIT = "identity:internal-app:edit";
    public static final String DICT_TYPE_QUERY = "system:dict-type:query";
    public static final String DICT_TYPE_CREATE = "system:dict-type:create";
    public static final String DICT_TYPE_UPDATE = "system:dict-type:update";
    public static final String DICT_TYPE_DELETE = "system:dict-type:delete";
    public static final String DICT_ENTRY_CREATE = "system:dict-entry:create";
    public static final String DICT_ENTRY_UPDATE = "system:dict-entry:update";
    public static final String DICT_ENTRY_DELETE = "system:dict-entry:delete";
    public static final String CONFIG_QUERY = "system:config:query";
    public static final String CONFIG_CREATE = "system:config:create";
    public static final String CONFIG_UPDATE = "system:config:update";
    public static final String CACHE_STATS = "system:cache:stats";
    public static final String CACHE_EVICT = "system:cache:evict";
    public static final String AUDIT_QUERY = "system:audit:query";

    private IdentityPermissions() {}
}
