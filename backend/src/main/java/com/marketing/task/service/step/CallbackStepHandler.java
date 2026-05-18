package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;
import org.springframework.stereotype.Component;

@Component
public class CallbackStepHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.CALLBACK;
    }

    @Override
    public void advance(StepContext context) {
    }
}
