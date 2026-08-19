package com.mkt.tracking.domain;

import java.util.regex.Pattern;

/** {@code <域>.<对象>.<动作>} (R28.2). */
public final class EventCodeSyntax {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]+)+");

    private EventCodeSyntax() {
    }

    public static boolean valid(String code) {
        return code != null && CODE.matcher(code).matches();
    }
}
