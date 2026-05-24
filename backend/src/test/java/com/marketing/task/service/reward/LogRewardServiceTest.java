package com.marketing.task.service.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.mapper.RewardRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogRewardServiceTest {

    @Mock
    private RewardRecordMapper rewardRecordMapper;

    @Test
    void reward_shouldThrowWhenNoHandlerMatchesConfig() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LogRewardService service = new LogRewardService(List.of(), new RewardConfigParser(), rewardRecordMapper);

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
