package com.mkt.db;

final class TinyintFlags {

    private TinyintFlags() {}

    /** TINYINT(1) is Boolean or Number depending on Connector/J. */
    static boolean isOneOrTrue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return false;
    }
}
