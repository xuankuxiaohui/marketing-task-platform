package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;
import org.springframework.stereotype.Component;

@Component
public class ClickStepHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.CLICK;
    }

    @Override
    public void advance(StepContext context) {
    }
}
