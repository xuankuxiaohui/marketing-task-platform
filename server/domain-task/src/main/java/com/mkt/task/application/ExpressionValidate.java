package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.ExpressionValidateCommand;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.ExpressionEngine;
import com.mkt.task.expression.ExpressionType;
import com.mkt.task.expression.ExpressionValidateResult;
import com.mkt.task.response.ExpressionValidateResponse;
import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Expression validate extracted from {@link TaskExpressionAppService} (R28-1).
 * Move-only: validate semantics unchanged.
 */
@Component
public class ExpressionValidate {

    private final TaskCrowdStore crowds;
    private final Clock clock;

    @Autowired
    public ExpressionValidate(TaskCrowdStore crowds, Clock clock) {
        this.crowds = crowds;
        this.clock = clock;
    }

    ExpressionValidateResponse validate(ExpressionValidateCommand command) {
        if (command == null || command.expression() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        ExpressionType type;
        try {
            type = ExpressionType.valueOf(command.type().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "type 仅允许 FILTER|BRANCH");
        }
        CrowdResolver resolver = new StoreCrowdResolver(crowds, null, null);
        ExpressionValidateResult result = ExpressionEngine.validate(command.expression(), type, clock, resolver);
        return new ExpressionValidateResponse(result.valid(), result.error(), result.nullAttrSample());
    }
}
