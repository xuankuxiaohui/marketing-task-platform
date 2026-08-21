package com.mkt.task.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.GrantSource;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.SkipReasons;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryRewardPort;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskProgressReportStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StepEngineTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskInstanceStore store;
    private MemoryTaskProgressReportStore reports;
    private MemoryRewardPort rewards;
    private EventPublisher events;
    private StepEngine engine;

    @BeforeEach
    void setUp() {
        store = new MemoryTaskInstanceStore();
        reports = new MemoryTaskProgressReportStore();
        rewards = new MemoryRewardPort();
        events = mock(EventPublisher.class);
        engine = new StepEngine(
                store, reports, events, Clock.fixed(NOW, ZoneOffset.UTC), new TaskSettings(), rewards);
    }

    @Test
    void clickCompletesAndCascadesToNextClick() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, clickThenClick(), attrs(), null);
        TaskInstanceStepEntity first = store.getStep(instance.getId(), "a");
        StepAdvanceResult result = engine.click(instance, first, clickThenClick(), attrs(), null);
        assertThat(result.step().getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(store.getStep(instance.getId(), "b").getStatus()).isEqualTo(StepStatuses.ACTIVE);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        verify(events).append(eq(EventCodes.TASK_STEP_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void clickCompletedStepIsIdempotent() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, singleClick(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "a");
        engine.click(instance, step, singleClick(), attrs(), null);
        TaskInstanceStepEntity again = store.getStep(instance.getId(), "a");
        StepAdvanceResult second = engine.click(instance, again, singleClick(), attrs(), null);
        assertThat(second.idempotent()).isTrue();
        assertThat(second.instance().getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        verify(events, times(1)).append(eq(EventCodes.TASK_STEP_COMPLETE), eq("task_instance"), any(), any());
        verify(events, times(1)).append(eq(EventCodes.TASK_INSTANCE_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void callbackCompletedStepIsIdempotentAndStoresBizNo() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, singleCallback(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "cb");
        engine.callback(instance, step, singleCallback(), attrs(), null, "biz-1");
        TaskInstanceStepEntity again = store.getStep(instance.getId(), "cb");
        StepAdvanceResult second = engine.callback(instance, again, singleCallback(), attrs(), null, "biz-2");
        assertThat(second.idempotent()).isTrue();
        assertThat(store.getStep(instance.getId(), "cb").getLastBizNo()).isEqualTo("biz-2");
    }

    @Test
    void clickInactiveIsStateMismatch() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, clickThenClick(), attrs(), null);
        TaskInstanceStepEntity later = store.getStep(instance.getId(), "b");
        assertThatThrownBy(() -> engine.click(instance, later, clickThenClick(), attrs(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
        assertThat(store.getStep(instance.getId(), "b").getStatus()).isEqualTo(StepStatuses.INACTIVE);
    }

    @Test
    void clickOnCallbackTypeIsStateMismatch() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, singleCallback(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "cb");
        assertThatThrownBy(() -> engine.click(instance, step, singleCallback(), attrs(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
    }

    @Test
    void skippedStepIsStateMismatch() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.PERMANENT;
        engine.enter(instance, singleReward(), attrs(), null);
        TaskInstanceStepEntity skipped = store.getStep(instance.getId(), "r");
        assertThat(skipped.getStatus()).isEqualTo(StepStatuses.SKIPPED);
        assertThatThrownBy(() -> engine.click(instance, skipped, singleReward(), attrs(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
    }

    @Test
    void expiredInstanceRejectsClick() {
        TaskInstanceEntity instance = insertInstance();
        instance.setExpireAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        engine.enter(instance, singleClick(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "a");
        assertThatThrownBy(() -> engine.click(instance, step, singleClick(), attrs(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
    }

    @Test
    void progressAccumulatesThenCompletesAtTarget() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = singleProgress(5);
        engine.enter(instance, snap, attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "p");
        StepAdvanceResult mid = engine.progress(instance, step, snap, attrs(), null, 2, "r1");
        assertThat(mid.step().getProgressCurrent()).isEqualTo(2);
        assertThat(mid.step().getStatus()).isEqualTo(StepStatuses.ACTIVE);
        TaskInstanceStepEntity again = store.getStep(instance.getId(), "p");
        StepAdvanceResult done = engine.progress(instance, again, snap, attrs(), null, 3, "r2");
        assertThat(done.step().getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(done.step().getProgressCurrent()).isEqualTo(5);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void duplicateReportIdDoesNotAccumulate() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = singleProgress(10);
        engine.enter(instance, snap, attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "p");
        engine.progress(instance, step, snap, attrs(), null, 3, "same");
        TaskInstanceStepEntity again = store.getStep(instance.getId(), "p");
        StepAdvanceResult dup = engine.progress(instance, again, snap, attrs(), null, 3, "same");
        assertThat(dup.idempotent()).isTrue();
        assertThat(dup.step().getProgressCurrent()).isEqualTo(3);
        assertThat(reports.rows).hasSize(1);
    }

    @Test
    void progressOnCompletedStepIsStateMismatch() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = singleProgress(1);
        engine.enter(instance, snap, attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "p");
        engine.progress(instance, step, snap, attrs(), null, 1, "r1");
        TaskInstanceStepEntity done = store.getStep(instance.getId(), "p");
        assertThatThrownBy(() -> engine.progress(instance, done, snap, attrs(), null, 1, "r2"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_STATE_MISMATCH);
    }

    @Test
    void progressValueOutOfRangeRejected() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = singleProgress(10);
        engine.enter(instance, snap, attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "p");
        assertThatThrownBy(() -> engine.progress(instance, step, snap, attrs(), null, 0, "r1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        assertThatThrownBy(() -> engine.progress(instance, step, snap, attrs(), null, 1001, "r2"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void progressCasExhaustionReturnsProcessing() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = singleProgress(10);
        engine.enter(instance, snap, attrs(), null);
        store.remainingAddProgressCasFailures = 3;
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "p");
        assertThatThrownBy(() -> engine.progress(instance, step, snap, attrs(), null, 1, "r1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.PROGRESS_PROCESSING);
        assertThat(store.getStep(instance.getId(), "p").getProgressCurrent()).isEqualTo(0);
    }

    @Test
    void clickCasExhaustionReturnsStepProcessing() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, singleClick(), attrs(), null);
        store.remainingCompleteCasFailures = 3;
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "a");
        assertThatThrownBy(() -> engine.click(instance, step, singleClick(), attrs(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_PROCESSING);
        assertThat(store.getStep(instance.getId(), "a").getStatus()).isEqualTo(StepStatuses.ACTIVE);
    }

    @Test
    void rewardGrantedCompletesAndEmitsEvents() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.GRANTED;
        engine.enter(instance, singleReward(), attrs(), null);
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(rewards.calls).hasSize(1);
        assertThat(rewards.calls.get(0).source()).isEqualTo(GrantSource.TASK_STEP);
        assertThat(rewards.calls.get(0).sourceId())
                .isEqualTo(String.valueOf(store.getStep(instance.getId(), "r").getId()));
        assertThat(rewards.calls.get(0).ctx().elapsedSeconds()).isNull();
        verify(events).append(eq(EventCodes.TASK_STEP_COMPLETE), eq("task_instance"), any(), any());
        verify(events).append(eq(EventCodes.TASK_INSTANCE_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void rewardWonContinuesCascade() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.WON;
        engine.enter(instance, singleReward(), attrs(), null);
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void rewardRetryableKeepsStepActive() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.RETRYABLE;
        engine.enter(instance, singleReward(), attrs(), null);
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.ACTIVE);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        verify(events, times(0)).append(eq(EventCodes.TASK_STEP_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void resumeFromRewardCompletesActiveStep() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.RETRYABLE;
        engine.enter(instance, singleReward(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "r");
        assertThat(step.getStatus()).isEqualTo(StepStatuses.ACTIVE);
        engine.resumeFromReward(instance, step, singleReward(), attrs(), null);
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void resumeFromRewardDoesNotReviveExpiredInstance() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.RETRYABLE;
        engine.enter(instance, singleReward(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "r");
        instance.setStatus(InstanceStatuses.EXPIRED);
        store.rows.put(instance.getId(), instance);
        engine.resumeFromReward(instance, step, singleReward(), attrs(), null);
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.ACTIVE);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.EXPIRED);
    }

    @Test
    void rewardPermanentSkipsAndCompletesInstance() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.PERMANENT;
        engine.enter(instance, singleReward(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "r");
        assertThat(step.getStatus()).isEqualTo(StepStatuses.SKIPPED);
        assertThat(step.getSkipReason()).isEqualTo(SkipReasons.GRANT_PERMANENT_FAILED);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void rewardPermanentStatusSkipsLikeException() {
        TaskInstanceEntity instance = insertInstance();
        rewards.behavior = MemoryRewardPort.Behavior.PERMANENT_STATUS;
        engine.enter(instance, singleReward(), attrs(), null);
        TaskInstanceStepEntity step = store.getStep(instance.getId(), "r");
        assertThat(step.getStatus()).isEqualTo(StepStatuses.SKIPPED);
        assertThat(step.getSkipReason()).isEqualTo(SkipReasons.GRANT_PERMANENT_FAILED);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
    }

    @Test
    void clickThenRewardPassesElapsedSeconds() {
        TaskInstanceEntity instance = insertInstance();
        SnapshotContent snap = clickThenReward();
        engine.enter(instance, snap, attrs(), null);
        TaskInstanceStepEntity click = store.getStep(instance.getId(), "a");
        engine.click(instance, click, snap, attrs(), null, "203.0.113.10", "dev-x");
        assertThat(store.getStep(instance.getId(), "r").getStatus()).isEqualTo(StepStatuses.COMPLETED);
        assertThat(rewards.calls).hasSize(1);
        assertThat(rewards.calls.get(0).ctx().elapsedSeconds()).isEqualTo(0L);
        assertThat(rewards.calls.get(0).ctx().ip()).isEqualTo("203.0.113.10");
        assertThat(rewards.calls.get(0).ctx().deviceId()).isEqualTo("dev-x");
    }

    private TaskInstanceEntity insertInstance() {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(1L);
        row.setTaskCode("t");
        row.setVersion(1);
        row.setSnapshotId(1L);
        row.setUserId(9L);
        row.setCycleKey("NONE");
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC));
        row.setStartedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        row.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        row.setSimulated(0);
        store.insert(row);
        return row;
    }

    private static SnapshotContent singleClick() {
        return snapshot(List.of(new TaskStepCommand("a", "A", 1, "CLICK", null, null)), List.of());
    }

    private static SnapshotContent singleCallback() {
        return snapshot(List.of(new TaskStepCommand("cb", "CB", 1, "CALLBACK", null, null)), List.of());
    }

    private static SnapshotContent singleProgress(int target) {
        return snapshot(List.of(new TaskStepCommand("p", "P", 1, "PROGRESS", target, null)), List.of());
    }

    private static SnapshotContent singleReward() {
        return snapshot(List.of(new TaskStepCommand("r", "R", 1, "REWARD", null, 8L)), List.of());
    }

    private static SnapshotContent clickThenClick() {
        return snapshot(
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "B", 2, "CLICK", null, null)),
                List.of());
    }

    private static SnapshotContent clickThenReward() {
        return snapshot(
                List.of(
                        new TaskStepCommand("a", "A", 1, "CLICK", null, null),
                        new TaskStepCommand("r", "R", 2, "REWARD", null, 8L)),
                List.of(new TaskTransitionCommand("a", "r", null, 0)));
    }

    private static SnapshotContent snapshot(List<TaskStepCommand> steps, List<TaskTransitionCommand> transitions) {
        return new SnapshotContent(
                "t",
                "t",
                null,
                "daily",
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                0,
                "NONE",
                null,
                null,
                null,
                null,
                new TaskGrayCommand("NONE", null, null, null, null),
                new TaskFilterCommand(null, List.of(), List.of()),
                steps,
                transitions,
                List.of());
    }

    private static UserAttributes attrs() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
