package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;

public interface StepHandler {
    StepType supports();

    default void onStepEnter(StepContext context) {
    }

    void advance(StepContext context);
}
