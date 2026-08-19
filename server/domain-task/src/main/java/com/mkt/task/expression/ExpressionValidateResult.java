package com.mkt.task.expression;

import java.util.List;

/** Validate-endpoint payload (design §4.4). */
public record ExpressionValidateResult(boolean valid, ExpressionError error, List<String> nullAttrSample) {

    public ExpressionValidateResult {
        nullAttrSample = nullAttrSample == null ? List.of() : List.copyOf(nullAttrSample);
    }

    public static ExpressionValidateResult ok(List<String> nullAttrSample) {
        return new ExpressionValidateResult(true, null, nullAttrSample);
    }

    public static ExpressionValidateResult fail(ExpressionError error) {
        return new ExpressionValidateResult(false, error, List.of());
    }
}
