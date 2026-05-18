package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;
import org.springframework.stereotype.Component;

@Component
public class ProgressStepHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.PROGRESS;
    }

    @Override
    public void advance(StepContext context) {
    }
}
