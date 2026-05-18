package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;

public interface RewardService {
    void reward(UserTaskInstance instance, TaskStep rewardStep);
}
