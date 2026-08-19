package com.mkt.task.response;

import com.mkt.task.expression.ExpressionError;
import java.util.List;

public record ExpressionValidateResponse(boolean valid, ExpressionError error, List<String> nullAttrSample) {}
