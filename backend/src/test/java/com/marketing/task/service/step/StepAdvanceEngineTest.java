package com.marketing.task.service.step;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.entity.UserTaskStepProgress;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.StepProgressStatus;
import com.marketing.task.domain.enums.StepType;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.mapper.UserTaskStepProgressMapper;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.task.TaskDefinitionCacheService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepAdvanceEngineTest {

    @Mock
    private TaskStepMapper taskStepMapper;
    @Mock
    private UserTaskInstanceMapper instanceMapper;
    @Mock
    private UserTaskStepProgressMapper progressMapper;
    @Mock
    private TaskDefinitionCacheService cacheService;
    @Mock
    private EventTrackingService eventTrackingService;

    private StepAdvanceEngine engine;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, TaskStep.class);
        TableInfoHelper.initTableInfo(assistant, UserTaskInstance.class);
        TableInfoHelper.initTableInfo(assistant, UserTaskStepProgress.class);
    }

    private UserTaskInstance instance;
    private TaskStep callbackStep;
    private TaskStep progressStep;
    private TaskStep rewardStep;

    @Captor
    private ArgumentCaptor<UserTaskStepProgress> progressCaptor;

    @BeforeEach
    void setUp() {
        // Create step handlers for the engine
        List<StepHandler> handlers = List.of(
                new ClickStepHandler(),
                new CallbackStepHandler(),
                new ProgressStepHandler(),
                new RewardStepHandler(mock(com.marketing.task.prize.service.PrizeService.class), mock(com.marketing.task.service.reward.RewardService.class), eventTrackingService)
        );
        engine = new StepAdvanceEngine(taskStepMapper, instanceMapper, progressMapper, handlers, cacheService, eventTrackingService);

        // Setup instance
        instance = new UserTaskInstance();
        instance.setId(100L);
        instance.setUserId("u_test");
        instance.setTaskId(1L);
        instance.setCycleKey("20260518");
        instance.setStatus(InstanceStatus.PENDING.name());
        instance.setCurrentStepSeq(1);

        // Setup steps: CALLBACK at seq 1, REWARD at seq 2
        callbackStep = new TaskStep();
        callbackStep.setId(10L);
        callbackStep.setTaskId(1L);
        callbackStep.setSeq(1);
        callbackStep.setType(StepType.CALLBACK.name());
        callbackStep.setCallbackEventKey("survey_completed");

        rewardStep = new TaskStep();
        rewardStep.setId(20L);
        rewardStep.setTaskId(1L);
        rewardStep.setSeq(2);
        rewardStep.setType(StepType.REWARD.name());
        rewardStep.setRewardConfigJson("{\"type\":\"point\",\"amount\":10}");

        // Progress test steps: PROGRESS at seq 1, REWARD at seq 2
        progressStep = new TaskStep();
        progressStep.setId(30L);
        progressStep.setTaskId(1L);
        progressStep.setSeq(1);
        progressStep.setType(StepType.PROGRESS.name());
        progressStep.setTargetValue(3);
    }

    // ---- CALLBACK tests ----

    @Test
    void callback_shouldAdvanceOnEventKeyMatch() {
        when(taskStepMapper.selectOne(any())).thenReturn(callbackStep);
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.updateById(any(UserTaskInstance.class))).thenReturn(1);
        when(cacheService.getSteps(any())).thenReturn(List.of(callbackStep, rewardStep));
        when(instanceMapper.update(any(), any())).thenReturn(1);
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        UserTaskInstance result = engine.callback(instance, "survey_completed");

        assertNotNull(result);
        verify(progressMapper, atLeastOnce()).insert(progressCaptor.capture());
        List<UserTaskStepProgress> allProgresses = progressCaptor.getAllValues();
        // First insert is the CALLBACK step, second from cascade (REWARD step)
        UserTaskStepProgress captured = allProgresses.get(0);
        assertEquals(StepProgressStatus.COMPLETED.name(), captured.getStatus());
        assertEquals(100L, captured.getInstanceId());
        assertEquals(10L, captured.getStepId());
        verify(instanceMapper, atLeast(2)).updateById(any(UserTaskInstance.class));
    }

    @Test
    void callback_shouldThrowOnTypeMismatch() {
        TaskStep clickStep = new TaskStep();
        clickStep.setId(10L);
        clickStep.setTaskId(1L);
        clickStep.setSeq(1);
        clickStep.setType(StepType.CLICK.name());

        when(taskStepMapper.selectOne(any())).thenReturn(clickStep);

        assertThrows(BusinessException.class, () -> engine.callback(instance, "any_key"));
    }

    @Test
    void callback_shouldThrowOnEventKeyMismatch() {
        when(taskStepMapper.selectOne(any())).thenReturn(callbackStep);

        assertThrows(BusinessException.class, () -> engine.callback(instance, "wrong_key"));
    }

    @Test
    void callback_shouldBeIdempotent() {
        when(taskStepMapper.selectOne(any())).thenReturn(callbackStep);
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        // Progress already completed
        UserTaskStepProgress completedProgress = new UserTaskStepProgress();
        completedProgress.setId(1L);
        completedProgress.setInstanceId(100L);
        completedProgress.setStepId(10L);
        completedProgress.setStatus(StepProgressStatus.COMPLETED.name());
        completedProgress.setProgressValue(0);
        when(progressMapper.selectOne(any())).thenReturn(completedProgress);

        // cascade needs a step list to fetch
        when(cacheService.getSteps(any())).thenReturn(List.of(callbackStep, rewardStep));

        // Should not throw
        UserTaskInstance result = engine.callback(instance, "survey_completed");
        assertNotNull(result);
        verify(progressMapper, never()).insert(any(UserTaskStepProgress.class));
        verify(progressMapper, never()).updateById(any(UserTaskStepProgress.class));
    }

    // ---- PROGRESS tests ----

    @Test
    void progress_shouldRemainInProgressWhenBelowTarget() {
        instance.setTaskId(1L);
        when(taskStepMapper.selectById(30L)).thenReturn(progressStep);
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        UserTaskInstance result = engine.progress(instance, 30L, 1);

        verify(progressMapper).insert(progressCaptor.capture());
        assertEquals(StepProgressStatus.IN_PROGRESS.name(), progressCaptor.getValue().getStatus());
        assertEquals(1, progressCaptor.getValue().getProgressValue());
    }

    @Test
    void progress_shouldAdvanceWhenReachingTarget() {
        when(taskStepMapper.selectById(30L)).thenReturn(progressStep);
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(instanceMapper.updateById(any(UserTaskInstance.class))).thenReturn(1);
        when(cacheService.getSteps(any())).thenReturn(List.of(progressStep, rewardStep));
        when(instanceMapper.update(any(), any())).thenReturn(1);
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        UserTaskInstance result = engine.progress(instance, 30L, 3);

        verify(progressMapper, atLeastOnce()).insert(progressCaptor.capture());
        // First insert is the PROGRESS step, second from cascade (REWARD step)
        UserTaskStepProgress captured = progressCaptor.getAllValues().get(0);
        assertEquals(StepProgressStatus.COMPLETED.name(), captured.getStatus());
        assertEquals(3, captured.getProgressValue());
    }

    @Test
    void progress_shouldBeIdempotentWhenAlreadyCompleted() {
        instance.setTaskId(1L);
        when(taskStepMapper.selectById(30L)).thenReturn(progressStep);

        UserTaskStepProgress completed = new UserTaskStepProgress();
        completed.setId(1L);
        completed.setInstanceId(100L);
        completed.setStepId(30L);
        completed.setStatus(StepProgressStatus.COMPLETED.name());
        completed.setProgressValue(3);
        when(progressMapper.selectOne(any())).thenReturn(completed);
        when(instanceMapper.selectById(100L)).thenReturn(instance);

        UserTaskInstance result = engine.progress(instance, 30L, 3);
        assertNotNull(result);
        // Should not throw or modify anything
        verify(progressMapper, never()).insert(any(UserTaskStepProgress.class));
        verify(progressMapper, never()).updateById(any(UserTaskStepProgress.class));
    }

    @Test
    void progress_shouldThrowOnStepNotBelongingToTask() {
        TaskStep otherTaskStep = new TaskStep();
        otherTaskStep.setId(99L);
        otherTaskStep.setTaskId(999L); // different task
        otherTaskStep.setType(StepType.PROGRESS.name());

        instance.setTaskId(1L);
        when(taskStepMapper.selectById(99L)).thenReturn(otherTaskStep);

        assertThrows(BusinessException.class, () -> engine.progress(instance, 99L, 1));
    }

    @Test
    void progress_shouldThrowOnTypeMismatch() {
        TaskStep clickStep = new TaskStep();
        clickStep.setId(10L);
        clickStep.setTaskId(1L);
        clickStep.setType(StepType.CLICK.name());

        instance.setTaskId(1L);
        when(taskStepMapper.selectById(10L)).thenReturn(clickStep);

        assertThrows(BusinessException.class, () -> engine.progress(instance, 10L, 1));
    }
}
