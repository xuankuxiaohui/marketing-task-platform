package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
        instances = new MemoryTaskInstanceStore(snapshots);
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
    void simulatedStartPersistsSimulatedFlag() {
        long taskId = publish("sim_go", "NONE", null);
        TaskStartResponse started = service.start(taskId, 9L, "203.0.113.1", null, "WEB", true);
        TaskInstanceEntity row = instances.getById(started.instanceId());
        assertThat(row.getSimulated()).isEqualTo(1);
    }

    @Test
    void startUsesReadCommitted() throws Exception {
        Transactional five = TaskClaimAppService.class
                .getMethod("start", long.class, long.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);
        Transactional six = TaskClaimAppService.class
                .getMethod(
                        "start",
                        long.class,
                        long.class,
                        String.class,
                        String.class,
                        String.class,
                        boolean.class)
                .getAnnotation(Transactional.class);
        assertThat(five.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(six.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void ukConflictFromMybatisPersistenceExceptionReturnsExisting() {
        long taskId = publish("uk_pe", "NONE", null);
        TaskStartResponse first = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        MemoryTaskInstanceStore spyStore = spy(instances);
        service = new TaskClaimAppService(
                definitions,
                snapshots,
                spyStore,
                crowds,
                mutex,
                users,
                risk,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                settings);
        doReturn(null).doCallRealMethod().when(spyStore).getByUserTaskCycle(eq(9L), eq(taskId), any());
        doThrow(new PersistenceException(new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'uk_user_task_cycle'", "23000", 1062)))
                .when(spyStore)
                .insert(any());
        TaskStartResponse again = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        assertThat(again.instanceId()).isEqualTo(first.instanceId());
        assertThat(instances.rows).hasSize(1);
    }

    @Test
    void ukConflictFromDuplicateKeyExceptionReturnsExisting() {
        long taskId = publish("uk_dk", "NONE", null);
        TaskStartResponse first = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        MemoryTaskInstanceStore spyStore = spy(instances);
        service = new TaskClaimAppService(
                definitions,
                snapshots,
                spyStore,
                crowds,
                mutex,
                users,
                risk,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                settings);
        doReturn(null).doCallRealMethod().when(spyStore).getByUserTaskCycle(eq(9L), eq(taskId), any());
        doThrow(new DuplicateKeyException("uk_user_task_cycle")).when(spyStore).insert(any());
        TaskStartResponse again = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        assertThat(again.instanceId()).isEqualTo(first.instanceId());
        assertThat(instances.rows).hasSize(1);
    }

    @Test
    void simulatedStartUkConflictFromMybatisPersistenceExceptionReturnsExisting() {
        long taskId = publish("uk_sim", "NONE", null);
        TaskStartResponse first = service.start(taskId, 9L, "1.1.1.1", null, "WEB", true);
        MemoryTaskInstanceStore spyStore = spy(instances);
        service = new TaskClaimAppService(
                definitions,
                snapshots,
                spyStore,
                crowds,
                mutex,
                users,
                risk,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                settings);
        doReturn(null).doCallRealMethod().when(spyStore).getByUserTaskCycle(eq(9L), eq(taskId), any());
        doThrow(new PersistenceException(new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'uk_user_task_cycle'", "23000", 1062)))
                .when(spyStore)
                .insert(any());
        TaskStartResponse again = service.start(taskId, 9L, "1.1.1.1", null, "WEB", true);
        assertThat(again.instanceId()).isEqualTo(first.instanceId());
        assertThat(instances.rows).hasSize(1);
        assertThat(instances.getById(again.instanceId()).getSimulated()).isEqualTo(1);
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
        long first = publish("a", "NONE", null, "mutex_a");
        service.start(first, 9L, "1.1.1.1", null, "WEB");
        long second = publish("b", "NONE", null, "mutex_a");
        assertThatThrownBy(() -> service.start(second, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
    }

    @Test
    void offlineTaskStillOccupiesItsUnchangedMutexGroup() {
        insertMutexGroup(false);
        long first = publish("offline_owner", "NONE", null, "mutex_a");
        TaskStartResponse started = service.start(first, 9L, "1.1.1.1", null, "WEB");
        TaskDefinitionEntity definition = definitions.getById(first);
        definition.setStatus("OFFLINE");
        definition.setOfflineAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        definitions.update(definition);
        long second = publish("offline_other", "NONE", null, "mutex_a");
        clearInvocations(events);

        assertThatThrownBy(() -> service.start(second, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
        assertThat(instances.rows).hasSize(1);
        assertThat(instances.getById(started.instanceId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        verify(events, never()).append(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void offlineMutexOccupancyUsesConfiguredCycleScope(boolean crossCycle) {
        insertMutexGroup(crossCycle);
        long first = publish("cycle_owner", "DAILY", null, "mutex_a");
        TaskStartResponse started = service.start(first, 9L, "1.1.1.1", null, "WEB");
        definitions.getById(first).setStatus("OFFLINE");
        long second = publish("cycle_other", "DAILY", null, "mutex_a");
        service = serviceAt(NOW.plusSeconds(86400));
        clearInvocations(events);

        if (crossCycle) {
            assertThatThrownBy(() -> service.start(second, 9L, "1.1.1.1", null, "WEB"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
            assertThat(instances.rows).hasSize(1);
            verify(events, never()).append(any(), any(), any(), any());
        } else {
            TaskStartResponse nextCycle = service.start(second, 9L, "1.1.1.1", null, "WEB");
            assertThat(instances.rows).hasSize(2);
            assertThat(instances.getById(nextCycle.instanceId()).getCycleKey()).isEqualTo("20260820");
        }
        assertThat(instances.getById(started.instanceId()).getCycleKey()).isEqualTo("20260819");
        assertThat(instances.getById(started.instanceId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "ABANDONED", "EXPIRED"})
    void terminalStartShortCircuitsOfflineVisibilityRiskAndDailyLimit(String status) {
        long taskId = publish("terminal_once", "NONE", null);
        TaskStartResponse started = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        TaskInstanceEntity original = instances.getById(started.instanceId());
        Long snapshotId = original.getSnapshotId();
        LocalDateTime expireAt = original.getExpireAt();
        original.setStatus(status);
        definitions.getById(taskId).setStatus("OFFLINE");
        settings.setDailyLimitPerUser(1);
        when(risk.check(eq(RiskScene.CLAIM), any())).thenReturn(new RiskVerdict(RiskAction.REJECT));
        clearInvocations(events, risk);

        TaskStartResponse again = service.start(taskId, 9L, "1.1.1.1", null, "WEB");

        assertThat(again.instanceId()).isEqualTo(started.instanceId());
        assertThat(again.instanceStatus()).isEqualTo(status);
        assertThat(instances.rows).hasSize(1);
        assertThat(instances.getById(again.instanceId()).getSnapshotId()).isEqualTo(snapshotId);
        assertThat(instances.getById(again.instanceId()).getExpireAt()).isEqualTo(expireAt);
        verify(risk, never()).check(any(), any());
        verify(events, never()).append(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void editingOrPublishingAnotherMutexGroupDoesNotMoveExistingSnapshotOccupancy(boolean publishRevision) {
        insertMutexGroup(false);
        TaskMutexGroupEntity otherGroup = new TaskMutexGroupEntity();
        otherGroup.setCode("mutex_b");
        otherGroup.setName("另一互斥组");
        otherGroup.setCrossCycle(0);
        mutex.insert(otherGroup);
        long owner = publish("snapshot_owner", "NONE", null, "mutex_a");
        TaskStartResponse started = service.start(owner, 9L, "1.1.1.1", null, "WEB");
        Long boundSnapshotId = instances.getById(started.instanceId()).getSnapshotId();
        TaskDefinitionEntity definition = definitions.getById(owner);
        definition.setMutexGroupId(otherGroup.getId());
        definition.setPendingRevision(1);
        if (publishRevision) {
            publishNextSnapshot(owner, "mutex_b");
            definition.setVersion(2);
            definition.setPendingRevision(0);
        }
        definitions.update(definition);
        long originalGroupTask = publish("snapshot_original_group", "NONE", null, "mutex_a");
        long newGroupTask = publish("snapshot_new_group", "NONE", null, "mutex_b");

        assertThatThrownBy(() -> service.start(originalGroupTask, 9L, "1.1.1.1", null, "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
        TaskStartResponse independent = service.start(newGroupTask, 9L, "1.1.1.1", null, "WEB");
        assertThat(independent.instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(instances.rows).hasSize(2);
        assertThat(instances.getById(started.instanceId()).getSnapshotId()).isEqualTo(boundSnapshotId);
        assertThat(instances.getById(started.instanceId()).getVersion()).isEqualTo(1);
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
    void publishedDraftCycleDoesNotChangeClaimCycleKey() {
        long taskId = publish("cyc", "DAILY", null);
        TaskDefinitionEntity definition = definitions.getById(taskId);
        definition.setCycleType("NONE");
        definitions.update(definition);
        TaskStartResponse started = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        TaskInstanceEntity row = instances.getById(started.instanceId());
        assertThat(row.getCycleKey()).isEqualTo("20260819");
    }

    @Test
    void expireAtIsNowPlusDaysWhenUnbounded() {
        long taskId = publishOpen("open");
        TaskStartResponse started = service.start(taskId, 9L, "1.1.1.1", null, "WEB");
        TaskInstanceEntity row = instances.getById(started.instanceId());
        assertThat(row.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(7L * 86400), ZoneOffset.UTC));
    }

    private long publish(String code, String cycleType, TaskGrayCommand gray) {
        return publish(code, cycleType, gray, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), null);
    }

    private long publish(String code, String cycleType, TaskGrayCommand gray, String mutexGroupCode) {
        return publish(
                code,
                cycleType,
                gray,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                mutexGroupCode);
    }

    private long publishOpen(String code) {
        return publish(code, "NONE", null, null, null, null);
    }

    private long publish(
            String code, String cycleType, TaskGrayCommand gray, Instant start, Instant end, String mutexGroupCode) {
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setStatus("PUBLISHED");
        entity.setVersion(1);
        entity.setCycleType(cycleType);
        entity.setGrayType(gray == null ? "NONE" : gray.type());
        entity.setSortWeight(0);
        entity.setDeleted(0);
        if (mutexGroupCode != null) {
            entity.setMutexGroupId(mutex.getByCode(mutexGroupCode).getId());
        }
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
                mutexGroupCode,
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

    private void insertMutexGroup(boolean crossCycle) {
        TaskMutexGroupEntity group = new TaskMutexGroupEntity();
        group.setCode("mutex_a");
        group.setName("互斥");
        group.setCrossCycle(crossCycle ? 1 : 0);
        mutex.insert(group);
    }

    private TaskClaimAppService serviceAt(Instant instant) {
        return new TaskClaimAppService(
                definitions,
                snapshots,
                instances,
                crowds,
                mutex,
                users,
                risk,
                events,
                Clock.fixed(instant, ZoneOffset.UTC),
                settings);
    }

    private void publishNextSnapshot(long taskId, String mutexGroupCode) {
        SnapshotContent previous = JsonUtil.fromJson(
                snapshots.getByTaskAndVersion(taskId, 1).getContent(), SnapshotContent.class);
        SnapshotContent next = new SnapshotContent(
                previous.code(),
                previous.name(),
                previous.description(),
                previous.category(),
                previous.iconUrl(),
                previous.badgeText(),
                previous.startTime(),
                previous.endTime(),
                previous.sortWeight(),
                previous.cycleType(),
                previous.cronExpr(),
                previous.specialStart(),
                previous.specialEnd(),
                mutexGroupCode,
                previous.gray(),
                previous.filter(),
                previous.steps(),
                previous.transitions(),
                previous.actions());
        TaskVersionSnapshotEntity snapshot = new TaskVersionSnapshotEntity();
        snapshot.setTaskId(taskId);
        snapshot.setVersion(2);
        snapshot.setContent(JsonUtil.toJson(next));
        snapshot.setPublishedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        snapshot.setPublishedBy(1L);
        snapshots.insert(snapshot);
    }

    private static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
