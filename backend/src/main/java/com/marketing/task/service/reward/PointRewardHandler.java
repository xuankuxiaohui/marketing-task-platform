package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.reward.RewardConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PointRewardHandler implements RewardHandler {

    @Override
    public boolean supports(RewardConfig config) {
        return "point".equals(config.getType());
    }

    @Override
    public void distribute(UserTaskInstance instance, TaskStep step, RewardConfig config) {
        int amount = config.getAmount() != null ? config.getAmount() : 0;
        log.info("[PointReward] 发放积分 userId={}, taskId={}, amount={}, instanceId={}",
                instance.getUserId(), instance.getTaskId(), amount, instance.getId());
    }
}
