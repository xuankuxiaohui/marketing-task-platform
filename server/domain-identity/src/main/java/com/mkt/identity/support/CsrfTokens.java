package com.mkt.identity.support;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class CsrfTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokens() {
    }

    public static String create() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean equal(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
