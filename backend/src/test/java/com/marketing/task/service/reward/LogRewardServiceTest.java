package com.marketing.task.service.reward;

import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LogRewardServiceTest {

    @Test
    void reward_shouldThrowWhenNoHandlerMatchesConfig() {
        LogRewardService service = new LogRewardService(List.of(), new RewardConfigParser());

        UserTaskInstance instance = new UserTaskInstance();
        instance.setId(100L);
        instance.setUserId("u_test");
        instance.setTaskId(1L);

        TaskStep rewardStep = new TaskStep();
        rewardStep.setId(10L);
        rewardStep.setRewardConfigJson("{\"type\":\"unknown\"}");

        assertThrows(BusinessException.class, () -> service.reward(instance, rewardStep));
    }
}
