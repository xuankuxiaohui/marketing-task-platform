package com.mkt.kernel;

import java.util.regex.Pattern;

/**
 * Asserts {@code <domain>.<scene>.<reason>} (or closed two-segment {@code common.<reason>}).
 */
public final class ErrorCodeFormat {

    private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9-]*");

    private ErrorCodeFormat() {
    }

    public static boolean isValid(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        String[] parts = code.split("\\.", -1);
        if (parts.length == 2) {
            return "common".equals(parts[0])
                    && isSegment(parts[1])
                    && ErrorSegments.isClosedCommonReason(parts[1]);
        }
        if (parts.length != 3) {
            return false;
        }
        return ErrorSegments.isKnownDomain(parts[0])
                && !"common".equals(parts[0])
                && ErrorSegments.isKnownScene(parts[0], parts[1])
                && isSegment(parts[2]);
    }

    public static void requireValid(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("invalid error code: " + code);
        }
    }

    private static boolean isSegment(String value) {
        return SEGMENT.matcher(value).matches();
    }
}
