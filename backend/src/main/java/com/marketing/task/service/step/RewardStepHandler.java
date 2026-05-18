package com.marketing.task.service.step;

import com.marketing.task.domain.enums.StepType;
import com.marketing.task.service.reward.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardStepHandler implements StepHandler {
    private final RewardService rewardService;

    @Override
    public StepType supports() {
        return StepType.REWARD;
    }

    @Override
    public void onStepEnter(StepContext context) {
        rewardService.reward(context.getInstance(), context.getStep());
    }

    @Override
    public void advance(StepContext context) {
        rewardService.reward(context.getInstance(), context.getStep());
    }
}
