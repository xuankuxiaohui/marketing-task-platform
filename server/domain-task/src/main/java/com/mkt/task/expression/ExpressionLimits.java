package com.mkt.task.expression;

/** Closed DSL limits (design §5.10 / R11.9). */
public final class ExpressionLimits {

    public static final int MAX_LENGTH = 1024;
    public static final int MAX_NODES = 200;

    private ExpressionLimits() {}
}
