package com.mkt.identity.response;

import java.util.List;

public record PermissionTreeNodeResponse(
        long id,
        long parentId,
        String type,
        String code,
        String name,
        String route,
        String component,
        String icon,
        int sort,
        String status,
        List<PermissionTreeNodeResponse> children) {

    public PermissionTreeNodeResponse {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
