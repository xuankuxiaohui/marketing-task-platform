package com.marketing.task.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.MutexGroup;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.Platform;
import com.marketing.task.domain.enums.TaskStatus;
import com.marketing.task.mapper.MutexGroupMapper;
import com.marketing.task.mapper.TaskDefinitionSnapshotMapper;
import com.marketing.task.mapper.TaskFilterMapper;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.EventTrackingService;
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
    private TaskStepPlatformMapper taskStepPlatformMapper;
    @Mock
    private TaskDefinitionSnapshotMapper snapshotMapper;
    @Mock
    private TaskDefinitionCacheService cacheService;
    @Mock
    private MutexGroupMapper mutexGroupMapper;
    @Mock
    private com.marketing.task.mapper.TaskStepTransitionMapper transitionMapper;
    @Mock
    private EventTrackingService eventTrackingService;

    private TaskService taskService;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, UserTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, MutexGroup.class);
    }

    private TaskService createService() {
        return new TaskService(taskMapper, instanceMapper, cycleKeyResolver,
                filterEvaluator, stepAdvanceEngine, taskStepMapper,
                taskFilterMapper, taskPlatformMapper, taskStepPlatformMapper,
                snapshotMapper, cacheService, mutexGroupMapper,
                transitionMapper, eventTrackingService);
    }

    private Task createTask(Long id, Long mutexGroupId) {
        Task task = new Task();
        task.setId(id);
        task.setCode("test_" + id);
        task.setName("Test Task " + id);
        task.setPeriodType("ONCE");
        task.setStatus(TaskStatus.PUBLISHED.name());
        task.setVersion(1);
        task.setMutexGroupId(mutexGroupId);
        return task;
    }

    private MutexGroup createMutexGroup(Long id, String scope) {
        MutexGroup group = new MutexGroup();
        group.setId(id);
        group.setName("Test Group " + id);
        group.setScope(scope);
        return group;
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
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 10L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "SAME_CYCLE");
        when(mutexGroupMapper.selectById(10L)).thenReturn(group);
        when(taskMapper.selectList(any())).thenReturn(List.of(taskB));
        when(instanceMapper.selectCount(any())).thenReturn(1L);

        taskService = createService();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.getOrCreateInstance(taskA, ctx));
        assertTrue(ex.getMessage().contains("互斥"));
    }

    @Test
    void mutex_shouldAllowWhenNoActiveInstanceInGroup() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 10L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "SAME_CYCLE");
        when(mutexGroupMapper.selectById(10L)).thenReturn(group);
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
    void mutex_shouldReturnExistingInstanceEvenWhenOtherTaskBlocked() {
        Task taskA = createTask(1L, 10L);
        UserContext ctx = createUserContext();

        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");

        UserTaskInstance existing = new UserTaskInstance();
        existing.setUserId("u_test");
        existing.setTaskId(1L);
        existing.setCycleKey("ONCE");
        existing.setStatus(InstanceStatus.PENDING.name());
        when(instanceMapper.selectOne(any())).thenReturn(existing);

        taskService = createService();
        UserTaskInstance result = taskService.getOrCreateInstance(taskA, ctx);
        assertNotNull(result);
        assertEquals(1L, result.getTaskId());
        verify(instanceMapper, never()).selectCount(any());
        verify(mutexGroupMapper, never()).selectById(any());
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
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void mutex_shouldAllowWhenMutexGroupNotFound() {
        Task task = createTask(1L, 999L);
        UserContext ctx = createUserContext();

        when(mutexGroupMapper.selectById(999L)).thenReturn(null);
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(task, ctx));
    }

    @Test
    void mutex_shouldAllowDifferentGroup() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 20L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "SAME_CYCLE");
        when(mutexGroupMapper.selectById(10L)).thenReturn(group);
        when(taskMapper.selectList(any())).thenReturn(List.of(taskB));
        when(instanceMapper.selectCount(any())).thenReturn(0L);
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(taskA, ctx));
    }

    // ---- listPublished mutex filtering ----

    @Test
    void listPublished_shouldHideMutexConflictWhenActiveInstanceExists() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 10L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "FULL_LIFECYCLE");

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(taskA, taskB));
        when(filterEvaluator.match(taskA, ctx)).thenReturn(true);
        when(filterEvaluator.match(taskB, ctx)).thenReturn(true);
        when(mutexGroupMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(group));

        UserTaskInstance active = new UserTaskInstance();
        active.setUserId("u_test");
        active.setTaskId(1L);
        active.setCycleKey("ONCE");
        active.setStatus(InstanceStatus.PENDING.name());

        when(instanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(active));
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(taskA));

        taskService = createService();
        List<com.marketing.task.domain.vo.TaskClientVO> result = taskService.listPublished(ctx);

        assertEquals(1, result.size());
        assertEquals(taskA.getCode(), result.get(0).getCode());
    }

    @Test
    void listPublished_shouldShowBothWhenNoActiveInstance() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 10L);
        UserContext ctx = createUserContext();

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(taskA, taskB));
        when(filterEvaluator.match(taskA, ctx)).thenReturn(true);
        when(filterEvaluator.match(taskB, ctx)).thenReturn(true);
        when(instanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        taskService = createService();
        List<com.marketing.task.domain.vo.TaskClientVO> result = taskService.listPublished(ctx);

        assertEquals(2, result.size());
    }

    @Test
    void listPublished_shouldAllowDifferentMutexGroups() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 20L);
        UserContext ctx = createUserContext();

        MutexGroup group10 = createMutexGroup(10L, "FULL_LIFECYCLE");

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(taskA, taskB));
        when(filterEvaluator.match(taskA, ctx)).thenReturn(true);
        when(filterEvaluator.match(taskB, ctx)).thenReturn(true);
        when(mutexGroupMapper.selectBatchIds(any())).thenReturn(List.of(group10));

        UserTaskInstance active = new UserTaskInstance();
        active.setUserId("u_test");
        active.setTaskId(1L);
        active.setCycleKey("ONCE");
        active.setStatus(InstanceStatus.PENDING.name());

        when(instanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(active));
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(taskA));

        taskService = createService();
        List<com.marketing.task.domain.vo.TaskClientVO> result = taskService.listPublished(ctx);

        assertEquals(2, result.size());
    }

    @Test
    void listPublished_shouldResolveDeadlockWhenBothTasksHaveActiveInstances() {
        Task taskA = createTask(1L, 10L);
        Task taskB = createTask(2L, 10L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "FULL_LIFECYCLE");

        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(taskA, taskB));
        when(filterEvaluator.match(taskA, ctx)).thenReturn(true);
        when(filterEvaluator.match(taskB, ctx)).thenReturn(true);
        when(mutexGroupMapper.selectBatchIds(any())).thenReturn(List.of(group));

        UserTaskInstance activeA = new UserTaskInstance();
        activeA.setUserId("u_test");
        activeA.setTaskId(1L);
        activeA.setCycleKey("ONCE");
        activeA.setStatus(InstanceStatus.PENDING.name());

        UserTaskInstance activeB = new UserTaskInstance();
        activeB.setUserId("u_test");
        activeB.setTaskId(2L);
        activeB.setCycleKey("ONCE");
        activeB.setStatus(InstanceStatus.PENDING.name());

        when(instanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(activeA, activeB));
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(taskA, taskB));

        taskService = createService();
        List<com.marketing.task.domain.vo.TaskClientVO> result = taskService.listPublished(ctx);

        assertEquals(1, result.size());
    }

    @Test
    void mutex_shouldAllowWhenNoOtherTasksInGroup() {
        Task task = createTask(1L, 10L);
        UserContext ctx = createUserContext();

        MutexGroup group = createMutexGroup(10L, "SAME_CYCLE");
        when(mutexGroupMapper.selectById(10L)).thenReturn(group);
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(cycleKeyResolver.resolve(any())).thenReturn("ONCE");
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.insert(any(UserTaskInstance.class))).thenReturn(1);
        when(stepAdvanceEngine.enter(any())).thenReturn(new UserTaskInstance());

        taskService = createService();
        assertDoesNotThrow(() -> taskService.getOrCreateInstance(task, ctx));
    }

    @Test
    void batchPublish_exceedingSizeLimit_shouldThrow() {
        taskService = createService();
        List<Long> tooManyIds = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        assertThrows(BusinessException.class, () -> taskService.batchPublish(tooManyIds));
    }

    @Test
    void batchOffline_exceedingSizeLimit_shouldThrow() {
        taskService = createService();
        List<Long> tooManyIds = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        assertThrows(BusinessException.class, () -> taskService.batchOffline(tooManyIds));
    }

    @Test
    void restoreTask_deletedTask_shouldRestore() {
        Task deletedTask = createTask(1L, null);
        deletedTask.setDeleted(1);
        when(taskMapper.selectDeletedById(1L)).thenReturn(deletedTask);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);

        taskService = createService();
        assertDoesNotThrow(() -> taskService.restoreTask(1L));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getDeleted());
        verify(cacheService).evict(1L);
    }

    @Test
    void restoreTask_nonDeletedTask_shouldThrow() {
        when(taskMapper.selectDeletedById(1L)).thenReturn(null);

        taskService = createService();
        assertThrows(BusinessException.class, () -> taskService.restoreTask(1L));
    }
}
