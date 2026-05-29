package com.marketing.task.service.step;

import com.marketing.common.EventType;
import com.marketing.context.UserContext;
import com.marketing.context.UserContextHolder;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.StepType;
import com.marketing.prize.service.GrantContext;
import com.marketing.prize.service.PrizeService;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.reward.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardStepHandler implements StepHandler {
    private final PrizeService prizeService;
    private final RewardService legacyRewardService;
    private final EventTrackingService eventTrackingService;

    @Override
    public StepType supports() {
        return StepType.REWARD;
    }

    @Override
    public void onStepEnter(StepContext context) {
        TaskStep step = context.getStep();
        UserTaskInstance instance = context.getInstance();

        eventTrackingService.track(EventType.REWARD_TRIGGERED, instance.getTaskId(), instance.getId(),
                step.getId(), instance.getUserId(), null, Map.of("rewardType",
                        step.getPrizeId() != null ? "prize" : "legacy"));

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
