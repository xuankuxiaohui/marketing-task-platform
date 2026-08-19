package com.mkt.identity.application;

import com.mkt.kernel.BusinessException;

/**
 * Login/register decision that commits with the surrounding transaction.
 * Callers throw {@link #orThrow()} after the proxy returns so lock counters and failure
 * audits are not rolled back (R1.2 / R1.3 / 05-security).
 */
public record AuthAttempt<T>(T value, BusinessException rejection) {

    public static <T> AuthAttempt<T> ok(T value) {
        return new AuthAttempt<>(value, null);
    }

    public static <T> AuthAttempt<T> rejected(BusinessException rejection) {
        return new AuthAttempt<>(null, rejection);
    }

    public T orThrow() {
        if (rejection != null) {
            throw rejection;
        }
        return value;
    }
}
