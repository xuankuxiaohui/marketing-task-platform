package com.marketing.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.domain.entity.TaskMetrics;
import com.marketing.task.mapper.EventLogMapper;
import com.marketing.task.mapper.TaskMetricsMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskMetricsSchedulerTest {

    @Mock
    private EventLogMapper eventLogMapper;
    @Mock
    private TaskMetricsMapper taskMetricsMapper;

    private TaskMetricsScheduler scheduler;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, TaskMetrics.class);
    }

    @BeforeEach
    void setUp() {
        scheduler = new TaskMetricsScheduler(eventLogMapper, taskMetricsMapper);
    }

    @Test
    void shouldAggregateViewsFromEvents() {
        Map<String, Object> event = Map.of("event_type", "TASK_VIEWED", "task_id", 1L);
        when(eventLogMapper.selectMaps(any())).thenReturn(List.of(event));
        when(taskMetricsMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(taskMetricsMapper.insert(any(TaskMetrics.class))).thenReturn(1);

        scheduler.aggregateMetrics();

        verify(taskMetricsMapper).insert(any(TaskMetrics.class));
    }

    @Test
    void shouldUpdateExistingMetrics() {
        Map<String, Object> event = Map.of("event_type", "INSTANCE_CREATED", "task_id", 1L);
        TaskMetrics existing = new TaskMetrics();
        existing.setId(10L);
        when(eventLogMapper.selectMaps(any())).thenReturn(List.of(event));
        when(taskMetricsMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(taskMetricsMapper.updateById(any(TaskMetrics.class))).thenReturn(1);

        scheduler.aggregateMetrics();

        verify(taskMetricsMapper).updateById(any(TaskMetrics.class));
    }

    @Test
    void shouldSkipEventsWithNullTaskId() {
        Map<String, Object> event = Map.of("event_type", "TASK_VIEWED", "task_id", (Object) null);
        when(eventLogMapper.selectMaps(any())).thenReturn(List.of(event));

        scheduler.aggregateMetrics();

        verify(taskMetricsMapper, never()).insert(any(TaskMetrics.class));
        verify(taskMetricsMapper, never()).updateById(any(TaskMetrics.class));
    }
}
