package com.mkt.identity.response;

import java.util.List;

public record MenuNodeResponse(
        long id, String name, String route, String component, String icon, int sort, List<MenuNodeResponse> children) {

    public MenuNodeResponse {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
