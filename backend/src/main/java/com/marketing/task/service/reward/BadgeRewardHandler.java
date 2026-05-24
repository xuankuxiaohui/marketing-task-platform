package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.reward.RewardConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BadgeRewardHandler implements RewardHandler {

    @Override
    public boolean supports(RewardConfig config) {
        return "badge".equals(config.getType());
    }

    @Override
    public void distribute(UserTaskInstance instance, TaskStep step, RewardConfig config) {
        String name = config.getName() != null ? config.getName() : "unknown";
        log.info("[BadgeReward] 发放徽章 userId={}, taskId={}, badge={}, instanceId={}",
                instance.getUserId(), instance.getTaskId(), name, instance.getId());
    }
}
