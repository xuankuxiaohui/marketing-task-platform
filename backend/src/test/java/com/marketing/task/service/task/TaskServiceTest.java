package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.cycle.CycleKeyResolver;
import com.marketing.task.service.filter.FilterEvaluator;
import com.marketing.task.service.step.StepAdvanceEngine;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserTaskInstanceMapper instanceMapper;
    @Mock
    private CycleKeyResolver cycleKeyResolver;
    @Mock
    private FilterEvaluator filterEvaluator;
    @Mock
    private StepAdvanceEngine stepAdvanceEngine;
    @Mock
    private TaskStepMapper taskStepMapper;
    @Mock
    private TaskFilterMapper taskFilterMapper;
    @Mock
    private TaskPlatformMapper taskPlatformMapper;
    @Mock
    private TaskDefinitionSnapshotMapper snapshotMapper;
    @Mock
    private TaskDefinitionCacheService cacheService;

    private TaskService taskService;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, UserTaskInstance.class);
    }

    private TaskService createService() {
        return new TaskService(taskMapper, instanceMapper, cycleKeyResolver,
                filterEvaluator, stepAdvanceEngine, taskStepMapper,
                taskFilterMapper, taskPlatformMapper, snapshotMapper, cacheService);
    }

    private Task createTask(Long id, String mutexGroupKey) {
        Task task = new Task();
        task.setId(id);
        task.setCode("test_" + id);
        task.setName("Test Task " + id);
        task.setPeriodType("ONCE");
        task.setStatus(TaskStatus.PUBLISHED.name());
        task.setVersion(1);
        task.setMutexGroupKey(mutexGroupKey);
        return task;
    }

    private UserContext createUserContext() {
        return UserContext.builder()
                .userId("u_test")
                .province("BJ")
                .platform(Platform.WEB)
                .role("user")
                .tags(java.util.Set.of())
                .level(1)
                .build();
    }

    // ---- Mutex tests ----

    @Test
    void mutex_shouldRejectWhenSameGroupHasActiveInstance() {
        Task taskA = createTask(1L, "group_test");
        Task taskB = createTask(2L, "group_test");
        UserContext ctx = createUserContext();

        // Task B has an active instance
        when(taskMapper.selectList(any())).thenReturn(List.of(taskB));
        when(instanceMapper.selectCount(any())).thenReturn(1L);

        taskService = createService();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.getOrCreateInstance(taskA, ctx));
        assertTrue(ex.getMessage().contains("互斥"));
    }

    @Test
    void mutex_shouldAllowWhenNoActiveInstanceInGroup() {
        Task taskA = createTask(1L, "group_test");
        Task taskB = createTask(2L, "group_test");
        UserContext ctx = createUserContext();

        when(taskMapper.selectList(any())).thenReturn(List.of(taskB));
        when(instanceMapper.selectCount(any())).thenReturn(0L);
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(taskA, ctx));
    }

    @Test
    void mutex_shouldSkipWhenNoMutexGroup() {
        Task task = createTask(1L, null);
        UserContext ctx = createUserContext();

        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(task, ctx));
        // Should not have queried for mutex tasks
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void mutex_shouldSkipWhenMutexGroupIsBlank() {
        Task task = createTask(1L, "   ");
        UserContext ctx = createUserContext();

        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(task, ctx));
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void mutex_shouldAllowDifferentGroup() {
        Task taskA = createTask(1L, "group_a");
        Task taskB = createTask(2L, "group_b");
        UserContext ctx = createUserContext();

        // Task B is in different group, should be ignored
        when(taskMapper.selectList(any())).thenReturn(List.of(taskB));
        when(instanceMapper.selectCount(any())).thenReturn(0L);
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(taskA, ctx));
    }

    @Test
    void mutex_shouldAllowWhenNoOtherTasksInGroup() {
        Task task = createTask(1L, "solo_group");
        UserContext ctx = createUserContext();

        // No other tasks in this mutex group
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(task, ctx));
    }
}
