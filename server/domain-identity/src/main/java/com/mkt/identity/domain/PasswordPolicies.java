package com.mkt.identity.domain;

import java.util.regex.Pattern;

/** R1.5 admin / R4.1 portal password rules. */
public final class PasswordPolicies {

    private static final Pattern ADMIN_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern ADMIN_LOWER = Pattern.compile("[a-z]");
    private static final Pattern ADMIN_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern ADMIN_SPECIAL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern PORTAL_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern PORTAL_DIGIT = Pattern.compile("[0-9]");

    private PasswordPolicies() {
    }

    public static boolean adminSatisfied(String password) {
        return password != null
                && password.length() >= 10
                && ADMIN_UPPER.matcher(password).find()
                && ADMIN_LOWER.matcher(password).find()
                && ADMIN_DIGIT.matcher(password).find()
                && ADMIN_SPECIAL.matcher(password).find();
    }

    public static boolean portalSatisfied(String password) {
        return password != null
                && password.length() >= 8
                && PORTAL_LETTER.matcher(password).find()
                && PORTAL_DIGIT.matcher(password).find();
    }
}
