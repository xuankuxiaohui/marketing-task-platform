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

    /** Login-optional (R28.3 / R30.6 / R32.1): missing token is anonymous; valid same-side token binds. */
    public static boolean portalOptionalAuth(String method, String path) {
        if (path == null) {
            return false;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/common/track/batch".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/common/ad/positions/")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)
                && path.startsWith("/api/common/ad/materials/")
                && path.endsWith("/dismiss")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            if ("/api/common/activity/activities".equals(path) || activityDetail(path)) {
                return true;
            }
            if ("/api/common/task/list".equals(path) || taskDetail(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean activityDetail(String path) {
        String prefix = "/api/common/activity/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        return digits(path.substring(prefix.length()));
    }

    private static boolean taskDetail(String path) {
        String prefix = "/api/common/task/";
        String suffix = "/detail";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return false;
        }
        String id = path.substring(prefix.length(), path.length() - suffix.length());
        return digits(id);
    }

    private static boolean digits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInfra(String path) {
        return path.startsWith("/actuator")
                || path.contains("/v3/api-docs")
                || path.startsWith("/error");
    }
}
