package com.mkt.task.expression;

/** Line/column position of a compile reject (design §5.10 step 1). */
public record ExpressionError(String position, String reason) {

    public static ExpressionError at(int line, int column, String reason) {
        return new ExpressionError(line + ":" + column, reason);
    }
}
