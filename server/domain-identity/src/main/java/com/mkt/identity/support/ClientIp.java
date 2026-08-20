package com.mkt.identity.support;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {}

    /**
     * Uses {@link HttpServletRequest#getRemoteAddr()} only. Reverse proxies must
     * overwrite the peer address (design §6.6 / 05-security §9).
     */
    public static String of(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "0.0.0.0" : remote;
    }
}
