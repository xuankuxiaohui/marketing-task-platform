package com.mkt.task.expression;

import com.mkt.kernel.BusinessException;
import com.mkt.task.support.TaskErrorCodes;

/** Compile-time reject of the closed DSL. */
public final class ExpressionCompileException extends BusinessException {

    private final ExpressionError detail;

    public ExpressionCompileException(ExpressionError detail) {
        super(TaskErrorCodes.EXPRESSION_VALIDATE_FAILED, detail.reason());
        this.detail = detail;
    }

    public ExpressionError detail() {
        return detail;
    }
}
