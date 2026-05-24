package com.marketing.task.service.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.RewardRecord;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.RewardStatus;
import com.marketing.task.domain.reward.RewardConfig;
import com.marketing.task.mapper.RewardRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    private RewardRecordMapper rewardRecordMapper;

    @Captor
    private ArgumentCaptor<RewardRecord> recordCaptor;

    private final PointRewardHandler pointHandler = new PointRewardHandler();
    private final CouponRewardHandler couponHandler = new CouponRewardHandler();
    private final BadgeRewardHandler badgeHandler = new BadgeRewardHandler();

    private LogRewardService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, RewardRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new LogRewardService(
                List.of(pointHandler, couponHandler, badgeHandler),
                new RewardConfigParser(),
                rewardRecordMapper);
    }

    private UserTaskInstance instance() {
        UserTaskInstance i = new UserTaskInstance();
        i.setId(100L);
        i.setUserId("u_test");
        i.setTaskId(1L);
        return i;
    }

    private TaskStep rewardStep(String configJson) {
        TaskStep s = new TaskStep();
        s.setId(20L);
        s.setTaskId(1L);
        s.setRewardConfigJson(configJson);
        return s;
    }

    // ---- Idempotency ----

    @Test
    void reward_shouldSkipWhenAlreadySuccess() {
        RewardRecord existing = new RewardRecord();
        existing.setId(1L);
        existing.setInstanceId(100L);
        existing.setStepId(20L);
        existing.setStatus(RewardStatus.SUCCESS.name());

        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":10}"));

        // No DB writes should happen
        verify(rewardRecordMapper, never()).insert(any(RewardRecord.class));
        verify(rewardRecordMapper, never()).updateById(any(RewardRecord.class));
    }

    @Test
    void reward_shouldRetryWhenPreviousFailed() {
        RewardRecord existing = new RewardRecord();
        existing.setId(1L);
        existing.setInstanceId(100L);
        existing.setStepId(20L);
        existing.setStatus(RewardStatus.FAILED.name());

        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        service.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":10}"));

        // Should update record to SUCCESS
        verify(rewardRecordMapper, atLeastOnce()).updateById(recordCaptor.capture());
        assertEquals(RewardStatus.SUCCESS.name(), recordCaptor.getValue().getStatus());
    }

    @Test
    void reward_shouldNotDoubleReward() {
        RewardRecord existing = new RewardRecord();
        existing.setId(1L);
        existing.setInstanceId(100L);
        existing.setStepId(20L);
        existing.setStatus(RewardStatus.SUCCESS.name());

        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":10}"));
        service.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":10}"));

        verify(rewardRecordMapper, never()).insert(any(RewardRecord.class));
        verify(rewardRecordMapper, never()).updateById(any(RewardRecord.class));
    }

    // ---- Point routing ----

    @Test
    void reward_shouldRouteToPointHandler() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        service.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":50}"));

        verify(rewardRecordMapper).insert(recordCaptor.capture());
        assertEquals("point", recordCaptor.getValue().getRewardType());
        assertEquals("100:20", recordCaptor.getValue().getIdempotentKey());
    }

    // ---- Coupon routing ----

    @Test
    void reward_shouldRouteToCouponHandler() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        service.reward(instance(), rewardStep("{\"type\":\"coupon\",\"amount\":1}"));

        verify(rewardRecordMapper).insert(recordCaptor.capture());
        assertEquals("coupon", recordCaptor.getValue().getRewardType());
    }

    // ---- Badge routing ----

    @Test
    void reward_shouldRouteToBadgeHandler() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        service.reward(instance(), rewardStep("{\"type\":\"badge\",\"name\":\"reader\"}"));

        verify(rewardRecordMapper).insert(recordCaptor.capture());
        assertEquals("badge", recordCaptor.getValue().getRewardType());
    }

    // ---- Failure handling ----

    @Test
    void reward_shouldRecordFailureWhenHandlerThrows() {
        // Build service with a handler that always throws
        RewardHandler failingHandler = new RewardHandler() {
            @Override
            public boolean supports(RewardConfig config) {
                return "point".equals(config.getType());
            }

            @Override
            public void distribute(UserTaskInstance instance, TaskStep step, RewardConfig config) {
                throw new RuntimeException("积分服务不可用");
            }
        };
        LogRewardService svc = new LogRewardService(
                List.of(failingHandler), new RewardConfigParser(), rewardRecordMapper);

        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        // Should not throw — failure is recorded, instance not blocked
        assertDoesNotThrow(() -> svc.reward(instance(), rewardStep("{\"type\":\"point\",\"amount\":10}")));

        verify(rewardRecordMapper).updateById(recordCaptor.capture());
        assertEquals(RewardStatus.FAILED.name(), recordCaptor.getValue().getStatus());
        assertEquals("积分服务不可用", recordCaptor.getValue().getErrorMessage());
    }

    // ---- Unknown type ----

    @Test
    void reward_shouldThrowWhenNoHandlerMatches() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                service.reward(instance(), rewardStep("{\"type\":\"unknown_type\"}")));
    }

    // ---- Record fields ----

    @Test
    void reward_shouldSetCorrectRecordFields() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        UserTaskInstance instance = instance();
        TaskStep step = rewardStep("{\"type\":\"point\",\"amount\":100}");

        service.reward(instance, step);

        verify(rewardRecordMapper).insert(recordCaptor.capture());
        RewardRecord r = recordCaptor.getValue();
        assertEquals(100L, r.getInstanceId());
        assertEquals(20L, r.getStepId());
        assertEquals("point", r.getRewardType());
        assertEquals("100:20", r.getIdempotentKey());
        assertNotNull(r.getRewardConfigJson());
        assertEquals(RewardStatus.SUCCESS.name(), r.getStatus());
    }

    // ---- Handler success updates record ----

    @Test
    void reward_shouldUpdateRecordToSuccessAfterDistribute() {
        when(rewardRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(rewardRecordMapper.insert(any(RewardRecord.class))).thenReturn(1);
        when(rewardRecordMapper.updateById(any(RewardRecord.class))).thenReturn(1);

        service.reward(instance(), rewardStep("{\"type\":\"badge\",\"name\":\"top_contributor\"}"));

        // Two calls: PENDING insert and then SUCCESS update
        verify(rewardRecordMapper).insert(any(RewardRecord.class));
        verify(rewardRecordMapper).updateById(recordCaptor.capture());
        assertEquals(RewardStatus.SUCCESS.name(), recordCaptor.getValue().getStatus());
        assertNull(recordCaptor.getValue().getErrorMessage());
    }
}
