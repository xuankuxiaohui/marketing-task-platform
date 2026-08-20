package com.mkt.task.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClaimEnterEngineTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskInstanceStore store;
    private EventPublisher events;
    private ClaimEnterEngine engine;

    @BeforeEach
    void setUp() {
        store = new MemoryTaskInstanceStore();
        events = mock(EventPublisher.class);
        engine = new ClaimEnterEngine(store, events, Clock.fixed(NOW, ZoneOffset.UTC), new TaskSettings());
    }

    @Test
    void allPassiveCompletesInstance() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, snapshot(List.of(new TaskStepCommand("a", "A", 1, "PASSIVE", null, null)), List.of()), attrs(), null);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        verify(events).append(eq(EventCodes.TASK_STEP_COMPLETE), eq("task_instance"), any(), any());
        verify(events).append(eq(EventCodes.TASK_INSTANCE_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void rewardStopsWithoutGrant() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(
                instance,
                snapshot(
                        List.of(
                                new TaskStepCommand("p", "P", 1, "PASSIVE", null, null),
                                new TaskStepCommand("r", "R", 2, "REWARD", null, 8L)),
                        List.of(new TaskTransitionCommand("p", "r", null, 0))),
                attrs(),
                null);
        List<TaskInstanceStepEntity> steps = store.listSteps(instance.getId());
        assertThat(steps).anyMatch(s -> "p".equals(s.getStepCode()) && StepStatuses.COMPLETED.equals(s.getStatus()));
        assertThat(steps).anyMatch(s -> "r".equals(s.getStepCode()) && StepStatuses.ACTIVE.equals(s.getStatus()));
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void conditionalEdgePicksMatchingTarget() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(
                instance,
                snapshot(
                        List.of(
                                new TaskStepCommand("p", "P", 1, "PASSIVE", null, null),
                                new TaskStepCommand("miss", "M", 2, "CLICK", null, null),
                                new TaskStepCommand("hit", "H", 3, "CLICK", null, null)),
                        List.of(
                                new TaskTransitionCommand("p", "miss", "province() = 'BJ'", 0),
                                new TaskTransitionCommand("p", "hit", "province() = 'GD'", 1))),
                attrs(),
                code -> false);
        assertThat(store.listSteps(instance.getId()))
                .anyMatch(s -> "hit".equals(s.getStepCode()) && StepStatuses.ACTIVE.equals(s.getStatus()));
        assertThat(store.listSteps(instance.getId()))
                .anyMatch(s -> "miss".equals(s.getStepCode()) && StepStatuses.INACTIVE.equals(s.getStatus()));
    }

    @Test
    void invalidConditionFallsBackToNextSeq() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(
                instance,
                snapshot(
                        List.of(
                                new TaskStepCommand("p", "P", 1, "PASSIVE", null, null),
                                new TaskStepCommand("c", "C", 2, "CLICK", null, null)),
                        List.of(new TaskTransitionCommand("p", "c", "not a valid ((", 0))),
                attrs(),
                null);
        assertThat(store.listSteps(instance.getId()))
                .anyMatch(s -> "c".equals(s.getStepCode()) && StepStatuses.ACTIVE.equals(s.getStatus()));
    }

    @Test
    void emptyStepsCompleteInstanceWithoutStepEvents() {
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, snapshot(List.of(), List.of()), attrs(), null);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        verify(events, times(1)).append(eq(EventCodes.TASK_INSTANCE_COMPLETE), eq("task_instance"), any(), any());
    }

    @Test
    void nullEventsDoNotThrow() {
        engine = new ClaimEnterEngine(store, null, Clock.fixed(NOW, ZoneOffset.UTC), new TaskSettings());
        TaskInstanceEntity instance = insertInstance();
        engine.enter(instance, snapshot(List.of(new TaskStepCommand("a", "A", 1, "PASSIVE", null, null)), List.of()), attrs(), null);
        assertThat(store.getById(instance.getId()).getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
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
        store.insert(row);
        return row;
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
