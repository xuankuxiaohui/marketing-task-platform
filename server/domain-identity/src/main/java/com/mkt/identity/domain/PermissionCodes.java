package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** OPERATION codes are {@code 域:资源:操作} (R2.2). */
public final class PermissionCodes {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]*:[a-z0-9-]+:[a-z0-9-]+");

    private PermissionCodes() {}

    public static boolean isOperationCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }
}
