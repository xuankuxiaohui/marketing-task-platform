package com.mkt.identity.domain;

import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;

/** R1.13 token prefix contract. */
public final class TokenPrefixes {

    private TokenPrefixes() {
    }

    public static String wrapAdmin(String raw) {
        return raw == null ? null : StpAdmin.PREFIX + raw;
    }

    public static String wrapClient(String raw) {
        return raw == null ? null : StpClient.PREFIX + raw;
    }

    public static String unwrapAdmin(String token) {
        if (token == null || !token.startsWith(StpAdmin.PREFIX)) {
            return null;
        }
        String raw = token.substring(StpAdmin.PREFIX.length());
        return raw.isBlank() ? null : raw;
    }

    public static String unwrapClient(String token) {
        if (token == null || !token.startsWith(StpClient.PREFIX)) {
            return null;
        }
        String raw = token.substring(StpClient.PREFIX.length());
        return raw.isBlank() ? null : raw;
    }

    public static boolean looksAdmin(String token) {
        return token != null && token.startsWith(StpAdmin.PREFIX);
    }

    public static boolean looksClient(String token) {
        return token != null && token.startsWith(StpClient.PREFIX);
    }
}
