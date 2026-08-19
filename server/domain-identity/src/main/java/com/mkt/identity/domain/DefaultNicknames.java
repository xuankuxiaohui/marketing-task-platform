package com.mkt.identity.domain;

/** R4.10: "用户" + last 6 digits of id. */
public final class DefaultNicknames {

    private DefaultNicknames() {
    }

    public static String of(long userId) {
        String digits = String.valueOf(userId);
        String suffix = digits.length() <= 6 ? digits : digits.substring(digits.length() - 6);
        String nickname = "用户" + suffix;
        return nickname.length() <= 30 ? nickname : nickname.substring(0, 30);
    }
}
