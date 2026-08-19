package com.mkt.portal.namespace;

import java.util.List;

/** portal-app allowed prefixes (design §2.3.3 / RL-08). */
final class NamespacePrefixes {

    static final List<String> VALUES = List.of("/api", "/internal", "/actuator");

    private NamespacePrefixes() {
    }

    static boolean allows(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        for (String prefix : VALUES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
