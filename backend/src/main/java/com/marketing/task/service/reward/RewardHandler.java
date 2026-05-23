package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.reward.RewardConfig;

public interface RewardHandler {
    boolean supports(RewardConfig config);
    void distribute(UserTaskInstance instance, TaskStep step, RewardConfig config);
}
