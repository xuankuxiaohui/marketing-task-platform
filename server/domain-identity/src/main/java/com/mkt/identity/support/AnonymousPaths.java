package com.mkt.identity.support;

/** Closed anonymous lists (design §4.2 / §4.9.0). */
public final class AnonymousPaths {

    private AnonymousPaths() {
    }

    public static boolean adminAnonymous(String method, String path) {
        if (path == null) {
            return false;
        }
        if (isInfra(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/admin/captcha".equals(path)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method) && "/admin/auth/login".equals(path);
    }

    public static boolean portalAnonymous(String method, String path) {
        if (path == null) {
            return false;
        }
        if (isInfra(path)) {
            return true;
        }
        if ("/internal".equals(path) || path.startsWith("/internal/")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/common/captcha".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/common/auth/username-available".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/common/auth/register".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/common/auth/login".equals(path)) {
            return true;
        }
        return portalOptionalAuth(method, path);
    }

    /** Login-optional (R28.3 / R30.6): missing token is anonymous; valid same-side token binds. */
    public static boolean portalOptionalAuth(String method, String path) {
        if (path == null) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/common/track/batch".equals(path)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && path.startsWith("/api/common/ad/positions/");
    }

    public static boolean isInfra(String path) {
        return path.startsWith("/actuator")
                || path.contains("/v3/api-docs")
                || path.startsWith("/error");
    }
}
