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
import org.springframework.stereotype.Service;

@Service
public class TaskExpressionAppService {

    private final TaskCrowdStore crowds;
    private final Clock clock;

    public TaskExpressionAppService(TaskCrowdStore crowds, Clock clock) {
        this.crowds = crowds;
        this.clock = clock;
    }

    public ExpressionValidateResponse validate(ExpressionValidateCommand command) {
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
