package com.marketing.task.service.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.common.BusinessException;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.mapper.RewardRecordMapper;
import com.marketing.task.service.EventTrackingService;
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
    @Mock
    private EventTrackingService eventTrackingService;

    @Test
    void reward_shouldThrowWhenNoHandlerMatchesConfig() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LogRewardService service = new LogRewardService(List.of(), new RewardConfigParser(), rewardRecordMapper, eventTrackingService);

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
