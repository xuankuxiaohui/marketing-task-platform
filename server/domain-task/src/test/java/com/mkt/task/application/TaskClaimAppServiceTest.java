package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskClaimAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryTaskInstanceStore instances;
    private MemoryTaskCrowdStore crowds;
    private MemoryTaskMutexGroupStore mutex;
    private UserAttributePort users;
    private RiskCheckPort risk;
    private EventPublisher events;
    private TaskSettings settings;
    private TaskClaimAppService service;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        instances = new MemoryTaskInstanceStore();
        crowds = new MemoryTaskCrowdStore();
        mutex = new MemoryTaskMutexGroupStore();
        users = mock(UserAttributePort.class);
        risk = mock(RiskCheckPort.class);
        events = mock(EventPublisher.class);
        settings = new TaskSettings();
        when(users.lockAndGet(9L)).thenReturn(active());
        when(users.attributes(9L)).thenReturn(active());
        when(risk.check(eq(RiskScene.CLAIM), any())).thenReturn(new RiskVerdict(RiskAction.PASS));
        service = new TaskClaimAppService(
                definitions,
                snapshots,
                instances,
                crowds,
                mutex,
                users,
                risk,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                settings);
    }

    @Test
    void startCreatesInstanceEntersPassiveAndAppendsStart() {
        long taskId = publish("daily_go", "NONE", null);
        TaskStartResponse started = service.start(taskId, 9L, "203.0.113.1", null, "WEB");
        assertThat(started.instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(started.currentStep().stepCode()).isEqualTo("click");
        assertThat(instances.rows).hasSize(1);
        assertThat(instances.listSteps(started.instanceId()))
                .anyMatch(step -> "go".equals(step.getStepCode()) && StepStatuses.COMPLETED.equals(step.getStatus()));
        verify(events).append(eq("task.instance.start"), eq("task_instance"), any(), any());
    }

    @Test
    void existingTerminalShortCircuitsBeforeVisibility() {
        long taskId = publish("once", "NONE", new TaskGrayCommand("RATIO", 0, null, null, null));
        TaskInstanceEntity abandoned = new TaskInstanceEntity();
        abandoned.setTaskId(taskId);
        abandoned.setTaskCode("once");
        abandoned.setVersion(1);
        abandoned.setSnapshotId(1L);
        abandoned.setUserId(9L);
        abandoned.setCycleKey("NONE");
        abandoned.setStatus(InstanceStatuses.ABANDONED);
        abandoned.setExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC));
        abandoned.setStartedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        abandoned.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        instances.insert(abandoned);
        TaskStartResponse again = service.start(taskId, 9L, "203.0.113.1", null, "WEB");
        assertThat(again.instanceId()).isEqualTo(abandoned.getId());
        assertThat(again.instanceStatus()).isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(instances.rows).hasSize(1);
        verify(risk, never()).check(any(), any());
    }

    @Test
    void disabledAccountIs403() {
        when(users.lockAndGet(9L))
                .thenReturn(new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.DISABLED));
        long taskId = publish("x", "NONE", null);
        assertThatThrownBy(() -> service.start(taskId, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.ACCOUNT_DISABLED);
        assertThat(instances.rows).isEmpty();
    }

    @Test
    void riskRejectDoesNotInsert() {
        when(risk.check(eq(RiskScene.CLAIM), any())).thenReturn(new RiskVerdict(RiskAction.REJECT));
        long taskId = publish("x", "NONE", null);
        assertThatThrownBy(() -> service.start(taskId, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.RISK_BLOCKED_GENERIC);
        assertThat(instances.rows).isEmpty();
    }

    @Test
    void dailyLimitRejectsNewInstance() {
        settings.setDailyLimitPerUser(1);
        long first = publish("a", "NONE", null);
        service.start(first, 9L, "1.1.1.1", null, "WEB");
        long second = publish("b", "NONE", null);
        assertThatThrownBy(() -> service.start(second, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_DAILY_LIMIT);
    }

    @Test
    void mutexBlocksOtherInProgress() {
        TaskMutexGroupEntity group = new TaskMutexGroupEntity();
        group.setCode("mutex_a");
        group.setName("互斥");
        group.setCrossCycle(0);
        mutex.insert(group);
        long first = publish("a", "NONE", null);
        definitions.getById(first).setMutexGroupId(group.getId());
        service.start(first, 9L, "1.1.1.1", null, "WEB");
        long second = publish("b", "NONE", null);
        definitions.getById(second).setMutexGroupId(group.getId());
        assertThatThrownBy(() -> service.start(second, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
    }

    @Test
    void notVisibleWhenGrayMisses() {
        long taskId = publish("g", "NONE", new TaskGrayCommand("RATIO", 0, null, null, null));
        assertThatThrownBy(() -> service.start(taskId, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_NOT_VISIBLE);
    }

    @Test
    void expireAtIsNowPlusDaysWhenUnbounded() {
        long taskId = publishOpen("open");
        TaskStartResponse started = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        TaskInstanceEntity row = instances.getById(started.instanceId());
        assertThat(row.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(7L * 86400), ZoneOffset.UTC));
    }

    private long publish(String code, String cycleType, TaskGrayCommand gray) {
        return publish(code, cycleType, gray, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
    }

    private long publishOpen(String code) {
        return publish(code, "NONE", null, null, null);
    }

    private long publish(String code, String cycleType, TaskGrayCommand gray, Instant start, Instant end) {
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setStatus("PUBLISHED");
        entity.setVersion(1);
        entity.setCycleType(cycleType);
        entity.setGrayType(gray == null ? "NONE" : gray.type());
        entity.setSortWeight(0);
        entity.setDeleted(0);
        definitions.insert(entity);
        SnapshotContent content = new SnapshotContent(
                code,
                code,
                null,
                "daily",
                null,
                null,
                start,
                end,
                0,
                cycleType,
                null,
                null,
                null,
                null,
                gray == null ? new TaskGrayCommand("NONE", null, null, null, null) : gray,
                new TaskFilterCommand(null, List.of(), List.of()),
                List.of(
                        new TaskStepCommand("go", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go", "click", null, 0)),
                List.of());
        TaskVersionSnapshotEntity snap = new TaskVersionSnapshotEntity();
        snap.setTaskId(entity.getId());
        snap.setVersion(1);
        snap.setContent(JsonUtil.toJson(content));
        snap.setPublishedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        snap.setPublishedBy(1L);
        snapshots.insert(snap);
        return entity.getId();
    }

    private static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
