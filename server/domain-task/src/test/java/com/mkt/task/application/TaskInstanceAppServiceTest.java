package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserRiskSummary;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.AbandonSources;
import com.mkt.task.domain.ActionMerger;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.response.InstanceEventView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.testsupport.MemoryInstanceEventStore;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskInstanceAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private MemoryTaskInstanceStore instances;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryInstanceEventStore timeline;
    private EventPublisher publisher;
    private RiskCheckPort risk;
    private TaskInstanceAppService service;

    @BeforeEach
    void setUp() {
        instances = new MemoryTaskInstanceStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        timeline = new MemoryInstanceEventStore();
        publisher = mock(EventPublisher.class);
        risk = mock(RiskCheckPort.class);
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(0L, List.of()));
        service = new TaskInstanceAppService(
                instances,
                snapshots,
                timeline,
                publisher,
                risk,
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);
    }

    @Test
    void pageFiltersAndDetailIncludesStepsAndEvents() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        instances.insert(row);
        insertStep(row.getId(), "click", "CLICK", "ACTIVE");
        timeline.rows.add(new InstanceEventView(EventCodes.TASK_INSTANCE_START, NOW, "{\"instanceId\":1}"));
        InstanceQuery query = new InstanceQuery(1L, 9L, InstanceStatuses.IN_PROGRESS, 0, null, null, PageQuery.of(1, 20));
        assertThat(service.page(query).total()).isEqualTo(1);
        AdminInstanceDetailResponse detail = service.get(row.getId());
        assertThat(detail.instance().status()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(detail.steps()).hasSize(1);
        assertThat(detail.events()).hasSize(1);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_NOT_FOUND);
    }

    @Test
    void adminAbandonFlipsAndEmitsEvent() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        instances.insert(row);
        InstanceAbandonResponse response = service.abandonAdmin(row.getId());
        assertThat(response.instanceStatus()).isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(instances.getById(row.getId()).getAbandonSource()).isEqualTo(AbandonSources.ADMIN);
        verify(publisher)
                .append(eq(EventCodes.TASK_INSTANCE_ABANDON), eq("task_instance"), eq(String.valueOf(row.getId())), any());
    }

    @Test
    void adminAbandonTerminalIsIdempotent() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        row.setStatus(InstanceStatuses.COMPLETED);
        instances.insert(row);
        assertThat(service.abandonAdmin(row.getId()).instanceStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        verify(publisher, never()).append(eq(EventCodes.TASK_INSTANCE_ABANDON), any(), any(), any());
    }

    @Test
    void userAbandonEmitsUserSource() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        instances.insert(row);
        assertThat(service.abandonUser(row.getId(), 9L, "203.0.113.1", null).instanceStatus())
                .isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(instances.getById(row.getId()).getAbandonSource()).isEqualTo(AbandonSources.USER);
        verify(risk).userSummary(9L);
        verify(risk, never()).check(any(), any());
    }

    @Test
    void userAbandonFrozenIs403() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        instances.insert(row);
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(1L, List.of(RiskListType.BLACK)));
        assertThatThrownBy(() -> service.abandonUser(row.getId(), 9L, "203.0.113.1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_FROZEN);
        assertThat(instances.getById(row.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        verify(risk, never()).check(any(), any());
    }

    @Test
    void userAbandonRejectsPastExpireAt() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.minusSeconds(1));
        instances.insert(row);
        assertThatThrownBy(() -> service.abandonUser(row.getId(), 9L, "203.0.113.1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
    }

    @Test
    void userAbandonRejectsOtherUser() {
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        instances.insert(row);
        assertThatThrownBy(() -> service.abandonUser(row.getId(), 8L, "203.0.113.1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.INSTANCE_NOT_FOUND);
    }

    @Test
    void expireDueFlipsPastExpireAtAndSkipsNull() {
        TaskInstanceEntity due = inProgress(1L, 9L, NOW.minusSeconds(1));
        instances.insert(due);
        TaskInstanceEntity future = inProgress(2L, 9L, NOW.plusSeconds(3600));
        instances.insert(future);
        TaskInstanceEntity unbounded = inProgress(3L, 9L, NOW.minusSeconds(1));
        instances.insert(unbounded);
        unbounded.setExpireAt(null);
        int flipped = service.expireDue();
        assertThat(flipped).isEqualTo(1);
        assertThat(instances.getById(due.getId()).getStatus()).isEqualTo(InstanceStatuses.EXPIRED);
        assertThat(instances.getById(future.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(instances.getById(unbounded.getId()).getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        verify(publisher)
                .append(eq(EventCodes.TASK_INSTANCE_EXPIRE), eq("task_instance"), eq(String.valueOf(due.getId())), any());
        assertThat(service.expireDue()).isZero();
    }

    @Test
    void snapshotActionsStayOnBoundSnapshot() {
        TaskVersionSnapshotEntity snap = new TaskVersionSnapshotEntity();
        snap.setTaskId(1L);
        snap.setVersion(1);
        snap.setContent(JsonUtil.toJson(snapshot("https://old.example")));
        snap.setPublishedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        snap.setPublishedBy(1L);
        snapshots.insert(snap);
        TaskInstanceEntity row = inProgress(1L, 9L, NOW.plusSeconds(86400));
        row.setSnapshotId(snap.getId());
        instances.insert(row);
        insertStep(row.getId(), "click", "CLICK", "ACTIVE");
        AdminInstanceDetailResponse detail = service.get(row.getId());
        assertThat(detail.steps()).hasSize(1);
        TaskActionCommand merged = ActionMerger.merge(snapshot("https://old.example").actions(), "click", "WEB");
        assertThat(merged.params()).containsEntry("url", "https://old.example");
    }

    private static SnapshotContent snapshot(String url) {
        return new SnapshotContent(
                "once",
                "once",
                "d",
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
                List.of(new TaskStepCommand("click", "点击", 1, "CLICK", null, null)),
                List.of(),
                List.of(new TaskActionCommand("TASK", null, "WEB", "LINK", Map.of("url", url), "去")));
    }

    private static TaskInstanceEntity inProgress(long taskId, long userId, Instant expireAt) {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(taskId);
        row.setTaskCode("once");
        row.setVersion(1);
        row.setSnapshotId(1L);
        row.setUserId(userId);
        row.setCycleKey("NONE");
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setExpireAt(LocalDateTime.ofInstant(expireAt, ZoneOffset.UTC));
        row.setStartedAt(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        row.setSimulated(0);
        row.setCreatedAt(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        return row;
    }

    private void insertStep(long instanceId, String code, String type, String status) {
        TaskInstanceStepEntity step = new TaskInstanceStepEntity();
        step.setInstanceId(instanceId);
        step.setStepCode(code);
        step.setSeq(1);
        step.setType(type);
        step.setStatus(status);
        step.setProgressCurrent(0);
        step.setVersion(0);
        instances.insertStep(step);
    }
}
