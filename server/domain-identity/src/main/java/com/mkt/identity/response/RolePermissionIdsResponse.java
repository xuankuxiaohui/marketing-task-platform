package com.mkt.identity.response;

import java.util.List;

public record RolePermissionIdsResponse(List<Long> permissionIds) {

    public RolePermissionIdsResponse {
        permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds);
    }
}
