package com.marketing.task.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ParticipationLog;
import com.marketing.task.activity.domain.enums.ActivityStatus;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import com.marketing.task.activity.mapper.ParticipationLogMapper;
import com.marketing.task.activity.service.ActivityRuleService;
import com.marketing.task.activity.service.ParticipationLogService;
import com.marketing.task.context.UserContext;
import com.marketing.task.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivityParticipationIntegrationTest {

    @Autowired private ActivityMapper activityMapper;
    @Autowired private ActivityStatsMapper activityStatsMapper;
    @Autowired private ParticipationLogMapper participationLogMapper;
    @Autowired private ActivityRuleService ruleService;
    @Autowired private ParticipationLogService participationLogService;

    private UserContext userCtx;

    @BeforeEach
    void setUp() {
        userCtx = UserContext.builder()
                .userId("100").province("BJ").level(5)
                .tags(Set.of("vip")).build();
        UserContextHolder.set(userCtx);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Activity createActivity(String code, String status) {
        Activity activity = new Activity();
        activity.setCode(code);
        activity.setName("测试活动 " + code);
        activity.setStatus(status);
        activity.setGrayType("ALL");
        activity.setStartTime(LocalDateTime.now().minusDays(1));
        activity.setEndTime(LocalDateTime.now().plusDays(30));
        activity.setDeleted(0);
        activity.setCreatedAt(LocalDateTime.now());
        activity.setUpdatedAt(LocalDateTime.now());
        activityMapper.insert(activity);
        return activity;
    }

    @Test
    void participate_onlineActivity_shouldRecordLogAndIncrementStats() {
        Activity activity = createActivity("ACT_TEST_1", ActivityStatus.ONLINE.name());

        ParticipationContext context = new ParticipationContext();
        context.setUserId(100L);
        context.setClientIp("192.168.1.1");

        RuleCheckResult result = ruleService.check(activity, context);
        participationLogService.recordParticipation(activity.getCode(), 100L, "192.168.1.1", result);

        assertTrue(result.isPassed());

        List<ParticipationLog> logs = participationLogMapper.selectList(
                new LambdaQueryWrapper<ParticipationLog>()
                        .eq(ParticipationLog::getActivityCode, "ACT_TEST_1"));
        assertFalse(logs.isEmpty());
        assertEquals("PASS", logs.get(0).getCheckerResult());
        assertEquals(100L, logs.get(0).getUserId());
    }

    @Test
    void participate_offlineActivity_shouldFailWithRuleCheck() {
        Activity activity = createActivity("ACT_TEST_2", ActivityStatus.OFFLINE.name());

        ParticipationContext context = new ParticipationContext();
        context.setUserId(100L);
        context.setClientIp("192.168.1.1");

        RuleCheckResult result = ruleService.check(activity, context);

        assertFalse(result.isPassed());
        assertEquals("ACTIVITY_NOT_ONLINE", result.getFailCode());
    }

    @Test
    void participate_twiceSameDay_shouldIncrementCountTo2() {
        Activity activity = createActivity("ACT_TEST_3", ActivityStatus.ONLINE.name());

        participationLogService.recordParticipation(activity.getCode(), 100L, "127.0.0.1", RuleCheckResult.pass());
        participationLogService.recordParticipation(activity.getCode(), 100L, "127.0.0.1", RuleCheckResult.pass());

        List<ParticipationLog> logs = participationLogMapper.selectList(
                new LambdaQueryWrapper<ParticipationLog>()
                        .eq(ParticipationLog::getActivityCode, "ACT_TEST_3"));
        assertEquals(2, logs.size());
    }

    @Test
    void participate_failedCheck_shouldRecordLogButNotIncrementStats() {
        Activity activity = createActivity("ACT_TEST_4", ActivityStatus.ONLINE.name());
        RuleCheckResult failResult = RuleCheckResult.fail("RATE_LIMIT", "请求过于频繁", "RATE");

        participationLogService.recordParticipation(activity.getCode(), 100L, "127.0.0.1", failResult);

        List<ParticipationLog> logs = participationLogMapper.selectList(
                new LambdaQueryWrapper<ParticipationLog>()
                        .eq(ParticipationLog::getActivityCode, "ACT_TEST_4"));
        assertFalse(logs.isEmpty());
        assertEquals("FAIL", logs.get(0).getCheckerResult());
        assertEquals("RATE_LIMIT", logs.get(0).getFailCode());
    }
}
