package com.mkt.task.expression;

/**
 * Sentinel for missing attributes (R11.9). Any comparison containing this value is false.
 * Boolean functions never return this.
 */
public final class NullAttr {

    public static final NullAttr INSTANCE = new NullAttr();

    private NullAttr() {}

    @Override
    public String toString() {
        return "NULL_ATTR";
    }
}
