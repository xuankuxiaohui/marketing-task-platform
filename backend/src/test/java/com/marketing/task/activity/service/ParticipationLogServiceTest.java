package com.marketing.task.activity.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.ParticipationLog;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import com.marketing.task.activity.mapper.ParticipationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipationLogServiceTest {

    @Mock
    private ParticipationLogMapper participationLogMapper;
    @Mock
    private ActivityStatsMapper activityStatsMapper;

    private ParticipationLogService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, ParticipationLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new ParticipationLogService(participationLogMapper, activityStatsMapper);
    }

    @Test
    void recordParticipation_withPassedResult_shouldInsertLogAndIncrementStats() {
        RuleCheckResult result = RuleCheckResult.pass();

        service.recordParticipation("ACT001", 100L, "192.168.1.1", result);

        ArgumentCaptor<ParticipationLog> captor = ArgumentCaptor.forClass(ParticipationLog.class);
        verify(participationLogMapper).insert(captor.capture());
        ParticipationLog log = captor.getValue();
        assertEquals("ACT001", log.getActivityCode());
        assertEquals(100L, log.getUserId());
        assertEquals("192.168.1.1", log.getClientIp());
        assertEquals("PASS", log.getCheckerResult());
        assertNull(log.getFailCode());
        assertNull(log.getFailMessage());

        verify(activityStatsMapper).incrementParticipantCount("ACT001");
    }

    @Test
    void recordParticipation_withFailedResult_shouldInsertLogOnly() {
        RuleCheckResult result = RuleCheckResult.fail("RATE_LIMIT", "请求过于频繁", "RATE");

        service.recordParticipation("ACT001", 100L, "192.168.1.1", result);

        ArgumentCaptor<ParticipationLog> captor = ArgumentCaptor.forClass(ParticipationLog.class);
        verify(participationLogMapper).insert(captor.capture());
        ParticipationLog log = captor.getValue();
        assertEquals("FAIL", log.getCheckerResult());
        assertEquals("RATE_LIMIT", log.getFailCode());
        assertEquals("请求过于频繁", log.getFailMessage());

        verify(activityStatsMapper, never()).incrementParticipantCount(any());
    }

    @Test
    void recordParticipation_shouldNotThrowOnMapperException() {
        doThrow(new RuntimeException("DB error")).when(participationLogMapper).insert(any(ParticipationLog.class));
        RuleCheckResult result = RuleCheckResult.pass();

        assertDoesNotThrow(() -> service.recordParticipation("ACT001", 100L, "127.0.0.1", result));
    }

    @Test
    void recordParticipation_shouldNotThrowOnStatsIncrementException() {
        doThrow(new RuntimeException("DB error")).when(activityStatsMapper).incrementParticipantCount(any());
        RuleCheckResult result = RuleCheckResult.pass();

        assertDoesNotThrow(() -> service.recordParticipation("ACT001", 100L, "127.0.0.1", result));
        verify(participationLogMapper).insert(any(ParticipationLog.class));
    }
}
