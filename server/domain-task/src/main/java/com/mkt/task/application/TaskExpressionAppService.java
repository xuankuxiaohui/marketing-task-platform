package com.mkt.task.application;

import com.mkt.task.command.ExpressionValidateCommand;
import com.mkt.task.response.ExpressionValidateResponse;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskExpressionAppService {

    private final ExpressionValidate expressionValidate;

    @Autowired
    public TaskExpressionAppService(
            TaskCrowdStore crowds, Clock clock, ExpressionValidate expressionValidate) {
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.expressionValidate =
                expressionValidate != null ? expressionValidate : new ExpressionValidate(crowds, clock);
    }

    public TaskExpressionAppService(TaskCrowdStore crowds, Clock clock) {
        this(crowds, clock, null);
    }

    public ExpressionValidateResponse validate(ExpressionValidateCommand command) {
        return expressionValidate.validate(command);
    }
}
