package com.mkt.identity.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Admin session + CSRF cookie attributes (NFR 安全 1 / R1.13). */
public final class AuthCookies {

    public static final String SESSION = "satoken";
    public static final String CSRF = "csrfToken";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final int MAX_AGE_SECONDS = 1800;

    private AuthCookies() {
    }

    public static void writeSession(HttpServletResponse response, String token) {
        add(response, SESSION, token, true);
    }

    public static void writeCsrf(HttpServletResponse response, String csrf) {
        add(response, CSRF, csrf, false);
    }

    public static void clear(HttpServletResponse response) {
        add(response, SESSION, "", true, 0);
        add(response, CSRF, "", false, 0);
    }

    public static String read(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    public static String readBearer(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static void add(HttpServletResponse response, String name, String value, boolean httpOnly) {
        add(response, name, value, httpOnly, MAX_AGE_SECONDS);
    }

    private static void add(
            HttpServletResponse response, String name, String value, boolean httpOnly, int maxAge) {
        StringBuilder header = new StringBuilder();
        header.append(name).append("=").append(value == null ? "" : value);
        header.append("; Path=/");
        header.append("; Max-Age=").append(maxAge);
        if (httpOnly) {
            header.append("; HttpOnly");
        }
        header.append("; Secure; SameSite=Strict");
        response.addHeader("Set-Cookie", header.toString());
    }
}
