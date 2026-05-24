package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.TaskMetrics;
import com.marketing.task.mapper.TaskMetricsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMetricsControllerTest {

    @Mock
    private TaskMetricsMapper taskMetricsMapper;

    @InjectMocks
    private AdminMetricsController controller;

    @Test
    void shouldReturnDashboard() {
        when(taskMetricsMapper.selectTopByDate(any(), anyInt())).thenReturn(List.of());

        var result = controller.dashboard();
        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertNotNull(data.get("today"));
        assertNotNull(data.get("topTasks"));
    }

    @Test
    void shouldReturnSummaryWithAggregatedValues() {
        TaskMetrics m1 = new TaskMetrics();
        m1.setTaskId(1L);
        m1.setViews(10L);
        m1.setParticipants(3L);
        m1.setCompletions(2L);
        m1.setRewardSuccess(1L);
        m1.setRewardFailure(0L);

        when(taskMetricsMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1));

        var result = controller.summary(1L);
        assertEquals(0, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(10L, (Long) data.get("totalViews"));
    }

    @Test
    void shouldReturnDailyMetrics() {
        when(taskMetricsMapper.selectDaily(anyLong(), any(), any())).thenReturn(List.of());

        var result = controller.daily(1L, "2026-05-01", "2026-05-24");
        assertEquals(0, result.getCode());
    }
}
