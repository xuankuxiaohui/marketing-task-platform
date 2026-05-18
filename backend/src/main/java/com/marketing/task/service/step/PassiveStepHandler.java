package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;
import org.springframework.stereotype.Component;

@Component
public class PassiveStepHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.PASSIVE;
    }

    @Override
    public void onStepEnter(StepContext context) {
    }

    @Override
    public void advance(StepContext context) {
    }
}
