/** Backend closed enums / seeds. Do not invent codes. */
export const STATUS = {
  ENABLED: "ENABLED",
  DISABLED: "DISABLED",
} as const;

export const ACCOUNT_TYPE = {
  ADMIN: "admin",
  PORTAL: "portal",
} as const;

export const SUPER_ADMIN_ROLE = "super-admin";
export const SUPER_ADMIN_USERNAME = "admin";

export const PERMS = {
  USER_QUERY: "identity:admin-user:query",
  USER_CREATE: "identity:admin-user:create",
  USER_UPDATE: "identity:admin-user:update",
  USER_DISABLE: "identity:admin-user:disable",
  USER_RESET: "identity:admin-user:reset-password",
  USER_DELETE: "identity:admin-user:delete",
  ROLE_QUERY: "identity:role:query",
  ROLE_CREATE: "identity:role:create",
  ROLE_UPDATE: "identity:role:update",
  ROLE_DELETE: "identity:role:delete",
  ROLE_ASSIGN: "identity:role:assign-permission",
  PERMISSION_QUERY: "identity:permission:query",
  PORTAL_QUERY: "identity:portal-user:query",
  PORTAL_PROFILE: "identity:portal-user:update-profile",
  PORTAL_DISABLE: "identity:portal-user:disable",
  PORTAL_RESET: "identity:portal-user:reset-password",
  PORTAL_DELETE: "identity:portal-user:delete",
  SESSION_QUERY: "identity:session:query",
  SESSION_KICK: "identity:session:kick",
  APP_QUERY: "identity:internal-app:query",
  APP_ADD: "identity:internal-app:add",
  APP_EDIT: "identity:internal-app:edit",
  DICT_TYPE_QUERY: "system:dict-type:query",
  DICT_TYPE_CREATE: "system:dict-type:create",
  DICT_TYPE_UPDATE: "system:dict-type:update",
  DICT_TYPE_DELETE: "system:dict-type:delete",
  DICT_ENTRY_CREATE: "system:dict-entry:create",
  CONFIG_QUERY: "system:config:query",
  CONFIG_CREATE: "system:config:create",
  CONFIG_UPDATE: "system:config:update",
  CACHE_STATS: "system:cache:stats",
  CACHE_EVICT: "system:cache:evict",
  AUDIT_QUERY: "system:audit:query",
} as const;

export const CONFIG_VALUE_TYPES = ["STRING", "NUMBER", "BOOL", "JSON"] as const;
