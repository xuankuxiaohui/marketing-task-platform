package com.mkt.task.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryRewardPort;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StepEngineDeadlineTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS, 0", "IN_PROGRESS, -1", "EXPIRED, 86400000"})
    void enterRejectsDueOrExpiredInstanceBeforeCreatingStepsOrGranting(String status, long deadlineOffsetMillis) {
        MemoryTaskInstanceStore instances = new MemoryTaskInstanceStore();
        MemoryRewardPort rewards = new MemoryRewardPort();
        EventPublisher events = mock(EventPublisher.class);
        StepEngine engine = new StepEngine(
                instances, null, events, Clock.fixed(NOW, ZoneOffset.UTC), new TaskSettings(), rewards);
        TaskInstanceEntity instance = instance(status, NOW.plusMillis(deadlineOffsetMillis));
        instances.insert(instance);
        SnapshotContent snapshot = new SnapshotContent(
                "deadline_enter", "直接发奖", null, "daily", null, null, null, null, 0, "NONE",
                null, null, null, null, new TaskGrayCommand("NONE", null, null, null, null),
                new TaskFilterCommand(null, List.of(), List.of()),
                List.of(new TaskStepCommand("reward", "奖励", 1, "REWARD", null, 8L)), List.of(), List.of());
        UserAttributes attrs = new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> engine.enter(instance, snapshot, attrs, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
        assertThat(instances.rows).hasSize(1);
        assertThat(instances.getById(instance.getId()).getStatus()).isEqualTo(status);
        assertThat(instances.listSteps(instance.getId())).isEmpty();
        assertThat(rewards.calls).isEmpty();
        verify(events, never()).append(any(), any(), any(), any());
    }

    private static TaskInstanceEntity instance(String status, Instant expireAt) {
        TaskInstanceEntity instance = new TaskInstanceEntity();
        instance.setTaskId(1L);
        instance.setTaskCode("deadline_enter");
        instance.setVersion(1);
        instance.setSnapshotId(1L);
        instance.setUserId(9L);
        instance.setCycleKey("NONE");
        instance.setStatus(status);
        instance.setExpireAt(LocalDateTime.ofInstant(expireAt, ZoneOffset.UTC));
        instance.setStartedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        instance.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        instance.setSimulated(0);
        return instance;
    }
}
