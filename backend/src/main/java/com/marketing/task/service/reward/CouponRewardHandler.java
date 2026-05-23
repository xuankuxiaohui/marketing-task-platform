package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.reward.RewardConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponRewardHandler implements RewardHandler {

    @Override
    public boolean supports(RewardConfig config) {
        return "coupon".equals(config.getType());
    }

    @Override
    public void distribute(UserTaskInstance instance, TaskStep step, RewardConfig config) {
        log.info("[CouponReward] 发放优惠券 userId={}, taskId={}, amount={}",
                instance.getUserId(), instance.getTaskId(), config.getAmount());
        // TODO: 对接真实优惠券系统 API
    }
}
