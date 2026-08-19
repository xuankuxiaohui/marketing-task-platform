package com.mkt.identity.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User permission union (R2.1): enabled roles only; disabled role bindings are kept but ignored.
 */
public final class PermissionUnion {

    private PermissionUnion() {}

    public record RoleSlice(boolean enabled, boolean builtIn, Set<String> operationCodes) {

        public RoleSlice {
            operationCodes = operationCodes == null ? Set.of() : Set.copyOf(operationCodes);
        }
    }

    public static Set<String> ofEnabledRoles(List<RoleSlice> roles) {
        Set<String> union = new LinkedHashSet<>();
        if (roles == null) {
            return union;
        }
        for (RoleSlice role : roles) {
            if (role != null && role.enabled()) {
                union.addAll(role.operationCodes());
            }
        }
        return union;
    }

    public static boolean allows(List<RoleSlice> roles, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        if (roles == null) {
            return false;
        }
        for (RoleSlice role : roles) {
            if (role != null && role.enabled() && role.operationCodes().contains(permissionCode)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasEnabledBuiltIn(List<RoleSlice> roles) {
        if (roles == null) {
            return false;
        }
        for (RoleSlice role : roles) {
            if (role != null && role.enabled() && role.builtIn()) {
                return true;
            }
        }
        return false;
    }
}
