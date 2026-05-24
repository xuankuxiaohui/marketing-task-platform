package com.marketing.task.service.step;

import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.StepType;
import com.marketing.task.prize.service.GrantContext;
import com.marketing.task.prize.service.PrizeService;
import com.marketing.task.service.reward.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardStepHandler implements StepHandler {
    private final PrizeService prizeService;
    private final RewardService legacyRewardService;

    @Override
    public StepType supports() {
        return StepType.REWARD;
    }

    @Override
    public void onStepEnter(StepContext context) {
        TaskStep step = context.getStep();
        UserTaskInstance instance = context.getInstance();

        if (step.getPrizeId() != null) {
            UserContext user = UserContextHolder.get();
            GrantContext grantCtx = GrantContext.builder()
                    .instanceId(instance.getId())
                    .taskId(instance.getTaskId())
                    .stepId(step.getId())
                    .cycleKey(instance.getCycleKey())
                    .province(user.getProvince())
                    .role(user.getRole())
                    .tags(user.getTags() != null ? String.join(",", user.getTags()) : null)
                    .orgId(user.getOrgId())
                    .level(user.getLevel())
                    .build();
            prizeService.grant(user, step.getPrizeId(),
                    step.getPrizeQuantity() != null ? step.getPrizeQuantity() : 1, grantCtx);
        } else {
            legacyRewardService.reward(instance, step);
        }
    }

    @Override
    public void advance(StepContext context) {
        onStepEnter(context);
    }
}
