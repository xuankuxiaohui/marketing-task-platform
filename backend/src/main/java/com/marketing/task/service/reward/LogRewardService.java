package com.marketing.task.service.reward;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogRewardService implements RewardService {

    @Override
    public void reward(UserTaskInstance instance, TaskStep rewardStep) {
        log.info("发奖给用户 userId={}, taskId={}, stepId={}, rewardConfig={}",
                instance.getUserId(), instance.getTaskId(), rewardStep.getId(), rewardStep.getRewardConfigJson());
    }
}
