package com.mkt.identity.domain;

import java.security.SecureRandom;

/** Internal appId / secret alphabet (design §4.2). */
public final class InternalAppSecrets {

    public static final int APP_ID_LENGTH = 16;
    public static final int SECRET_LENGTH = 43;
    public static final int ROTATE_WINDOW_HOURS = 24;

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private InternalAppSecrets() {}

    public static String randomAppId(SecureRandom random) {
        return randomToken(random, APP_ID_LENGTH);
    }

    public static String randomSecret(SecureRandom random) {
        return randomToken(random, SECRET_LENGTH);
    }

    private static String randomToken(SecureRandom random, int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
