package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.cycle.CycleKeyResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCycleSchedulerTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserTaskInstanceMapper instanceMapper;
    @Mock
    private CycleKeyResolver cycleKeyResolver;

    private TaskCycleScheduler scheduler;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, UserTaskInstance.class);
    }

    @BeforeEach
    void setUp() {
        scheduler = new TaskCycleScheduler(taskMapper, instanceMapper, cycleKeyResolver);
    }

    private Task createTask(Long id, String periodType) {
        Task task = new Task();
        task.setId(id);
        task.setCode("test_" + id);
        task.setName("Test Task " + id);
        task.setPeriodType(periodType);
        task.setStatus("PUBLISHED");
        return task;
    }

    @Test
    void shouldActivateNewCycleForMonthlyTask() {
        Task task = createTask(1L, "MONTHLY");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(cycleKeyResolver.resolve(task)).thenReturn("202605");
        when(instanceMapper.selectCount(any())).thenReturn(0L);

        scheduler.scanAndActivateCycles();

        ArgumentCaptor<LambdaQueryWrapper<UserTaskInstance>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(instanceMapper).selectCount(captor.capture());
        verify(cycleKeyResolver).resolve(task);
    }

    @Test
    void shouldActivateNewCycleForCronTask() {
        Task task = createTask(2L, "CRON");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(cycleKeyResolver.resolve(task)).thenReturn("202605241430");
        when(instanceMapper.selectCount(any())).thenReturn(0L);

        scheduler.scanAndActivateCycles();

        verify(instanceMapper).selectCount(any());
    }

    @Test
    void shouldNotQueryInstanceCountForSameCycle() {
        Task task = createTask(1L, "MONTHLY");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(cycleKeyResolver.resolve(task)).thenReturn("202605");

        // First scan activates
        scheduler.scanAndActivateCycles();
        verify(instanceMapper, times(1)).selectCount(any());

        // Second scan with same cycle key should skip
        scheduler.scanAndActivateCycles();
        verify(instanceMapper, times(1)).selectCount(any());
    }

    @Test
    void shouldActivateWhenCycleKeyChanges() {
        Task task = createTask(1L, "CRON");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(cycleKeyResolver.resolve(task)).thenReturn("202605241430");

        scheduler.scanAndActivateCycles();
        verify(instanceMapper, times(1)).selectCount(any());

        // Cycle key changes (next 5-minute window)
        when(cycleKeyResolver.resolve(task)).thenReturn("202605241435");
        scheduler.scanAndActivateCycles();
        verify(instanceMapper, times(2)).selectCount(any());
    }

    @Test
    void shouldOnlyScanPublishedTasks() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        scheduler.scanAndActivateCycles();

        ArgumentCaptor<LambdaQueryWrapper<Task>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectList(captor.capture());
        verify(instanceMapper, never()).selectCount(any());
    }

    @Test
    void shouldHandleMultipleTasks() {
        Task monthly = createTask(1L, "MONTHLY");
        Task cron = createTask(2L, "CRON");
        when(taskMapper.selectList(any())).thenReturn(List.of(monthly, cron));
        when(cycleKeyResolver.resolve(monthly)).thenReturn("202605");
        when(cycleKeyResolver.resolve(cron)).thenReturn("202605241430");
        when(instanceMapper.selectCount(any())).thenReturn(3L, 0L);

        scheduler.scanAndActivateCycles();

        verify(cycleKeyResolver).resolve(monthly);
        verify(cycleKeyResolver).resolve(cron);
        verify(instanceMapper, times(2)).selectCount(any());
    }

    @Test
    void shouldReportExistingInstanceCount() {
        Task task = createTask(1L, "MONTHLY");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(cycleKeyResolver.resolve(task)).thenReturn("202605");
        when(instanceMapper.selectCount(any())).thenReturn(5L);

        scheduler.scanAndActivateCycles();

        verify(instanceMapper).selectCount(any());
    }
}
