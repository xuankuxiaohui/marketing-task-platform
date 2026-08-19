package com.mkt.kernel.audit;

/**
 * One {@code audit.log} per request: service appenders mark, AOP skips if already written.
 */
public final class AuditOnce {

    private static final ThreadLocal<Boolean> WRITTEN = new ThreadLocal<>();

    private AuditOnce() {}

    public static boolean written() {
        return Boolean.TRUE.equals(WRITTEN.get());
    }

    public static void mark() {
        WRITTEN.set(Boolean.TRUE);
    }

    public static void clear() {
        WRITTEN.remove();
    }
}
