package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskPortalAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryTaskInstanceStore instances;
    private UserAttributePort users;
    private RiskCheckPort risk;
    private TaskPortalAppService service;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        instances = new MemoryTaskInstanceStore();
        users = mock(UserAttributePort.class);
        risk = mock(RiskCheckPort.class);
        when(users.attributes(9L)).thenReturn(active());
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(0L, List.of()));
        service = new TaskPortalAppService(
                definitions,
                snapshots,
                instances,
                new MemoryTaskCrowdStore(),
                users,
                risk,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listReturnsPublishedVisibleSorted() {
        long first = publish("b_task", 2);
        publish("a_task", 1);
        PageData<TaskCardView> page = service.list(9L, null, 1, 20);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).extracting(TaskCardView::taskCode).containsExactly("a_task", "b_task");
        assertThat(page.records().get(0).userStatus()).isEqualTo(InstanceStatuses.NOT_STARTED);
        assertThat(first).isPositive();
    }

    @Test
    void anonymousListShowsPublicCardsAndHidesPersonalized() {
        publish("open", 1);
        publish("hidden", 2, new TaskGrayCommand("RATIO", 100, null, null, null));
        publishFiltered("filtered", "province() = 'GD'");
        PageData<TaskCardView> page = service.list(null, null, 1, 20);
        assertThat(page.records()).extracting(TaskCardView::taskCode).containsExactly("open");
        assertThat(page.records().get(0).userStatus()).isEqualTo(InstanceStatuses.NOT_STARTED);
    }

    @Test
    void anonymousDetailPublicNotStartedGrayOffline() {
        long open = publish("open", 1);
        long hidden = publish("hidden", 1, new TaskGrayCommand("RATIO", 0, null, null, null));
        assertThat(service.detail(open, null, "WEB").status()).isEqualTo(InstanceStatuses.NOT_STARTED);
        assertThat(service.detail(hidden, null, "WEB").status()).isEqualTo(InstanceStatuses.OFFLINE);
    }

    @Test
    void blacklistUserGetsEmptyList() {
        publish("a_task", 1);
        when(risk.userSummary(9L)).thenReturn(new UserRiskSummary(1L, List.of(RiskListType.BLACK)));
        assertThat(service.list(9L, null, 1, 20).records()).isEmpty();
    }

    @Test
    void inProgressOverlaySurvivesGrayMiss() {
        long taskId = publish("hidden", 1, new TaskGrayCommand("RATIO", 100, null, null, null));
        TaskInstanceEntity row = inProgress(taskId, snapshots.listByTaskId(taskId).get(0).getId());
        instances.insert(row);
        definitions.getById(taskId);
        SnapshotContent updated = JsonUtil.fromJson(
                snapshots.getByTaskAndVersion(taskId, 1).getContent(), SnapshotContent.class);
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, 1);
        snap.setContent(JsonUtil.toJson(new SnapshotContent(
                updated.code(),
                updated.name(),
                updated.description(),
                updated.category(),
                updated.iconUrl(),
                updated.badgeText(),
                updated.startTime(),
                updated.endTime(),
                updated.sortWeight(),
                updated.cycleType(),
                updated.cronExpr(),
                updated.specialStart(),
                updated.specialEnd(),
                updated.mutexGroupCode(),
                new TaskGrayCommand("RATIO", 0, null, null, null),
                updated.filter(),
                updated.steps(),
                updated.transitions(),
                updated.actions())));
        PageData<TaskCardView> page = service.list(9L, null, 1, 20);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).userStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void detailOfflineWhenNotVisible() {
        long taskId = publish("gone", 1, new TaskGrayCommand("RATIO", 0, null, null, null));
        TaskDetailResponse detail = service.detail(taskId, 9L, "WEB");
        assertThat(detail.status()).isEqualTo(InstanceStatuses.OFFLINE);
        assertThat(detail.instanceId()).isNull();
    }

    @Test
    void detailNotStartedWhenVisible() {
        long taskId = publish("ok", 1);
        TaskDetailResponse detail = service.detail(taskId, 9L, "WEB");
        assertThat(detail.status()).isEqualTo(InstanceStatuses.NOT_STARTED);
        assertThat(detail.stepsPreview()).isNotEmpty();
        assertThat(detail.task().name()).isEqualTo("ok");
    }

    @Test
    void currentCycleInProgressSurvivesGrayMissWhenPriorCycleAlsoOpen() {
        long taskId = publishDaily("daily_g", new TaskGrayCommand("RATIO", 0, null, null, null));
        long snapshotId = snapshots.listByTaskId(taskId).get(0).getId();
        instances.insert(inProgressAt(taskId, snapshotId, "20260818"));
        TaskInstanceEntity today = inProgressAt(taskId, snapshotId, "20260819");
        instances.insert(today);
        PageData<TaskCardView> page = service.list(9L, null, 1, 20);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).userStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(service.detail(taskId, 9L, "WEB").instanceId()).isEqualTo(today.getId());
    }

    @Test
    void mineReturnsCompletedInstanceForCompletedTab() {
        long taskId = publish("demo-claim-01", 1);
        long snapshotId = snapshots.listByTaskId(taskId).get(0).getId();
        TaskInstanceEntity done = inProgress(taskId, snapshotId);
        done.setStatus(InstanceStatuses.COMPLETED);
        done.setTaskCode("demo-claim-01");
        instances.insert(done);
        instances.insert(inProgressAt(taskId, snapshotId, "other"));

        PageData<MineTaskView> completed = service.mine(9L, "COMPLETED", null, 1, 20);
        assertThat(completed.total()).isEqualTo(1);
        assertThat(completed.records()).extracting(MineTaskView::taskName).containsExactly("demo-claim-01");
        assertThat(completed.records()).extracting(MineTaskView::status).containsExactly(InstanceStatuses.COMPLETED);

        PageData<MineTaskView> inProgress = service.mine(9L, "IN_PROGRESS", null, 1, 20);
        assertThat(inProgress.records()).extracting(MineTaskView::status).containsExactly(InstanceStatuses.IN_PROGRESS);
        assertThat(inProgress.records()).extracting(MineTaskView::instanceId).doesNotContain(done.getId());

        PageData<MineTaskView> folded = service.mine(9L, "completed", null, 1, 20);
        assertThat(folded.records()).extracting(MineTaskView::instanceId).containsExactly(done.getId());
        assertThat(service.mine(9L, "SUCCESS", null, 1, 20).records())
                .extracting(MineTaskView::instanceId)
                .containsExactly(done.getId());
        assertThat(service.mine(9L, "DONE", null, 1, 20).records())
                .extracting(MineTaskView::instanceId)
                .containsExactly(done.getId());
        assertThat(service.mine(9L, "1", null, 1, 20).records())
                .extracting(MineTaskView::instanceId)
                .containsExactly(done.getId());
        assertThat(service.mine(9L, "COMPLETED", "0", 1, 20).records())
                .extracting(MineTaskView::instanceId)
                .containsExactly(done.getId());
    }

    @Test
    void missingProvinceFilterHidesTask() {
        long taskId = publishFiltered("prov", "province() = 'BJ'");
        when(users.attributes(9L))
                .thenReturn(new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.ACTIVE));
        assertThat(service.list(9L, null, 1, 20).records()).isEmpty();
        assertThat(service.detail(taskId, 9L, "WEB").status()).isEqualTo(InstanceStatuses.OFFLINE);
    }

    private long publish(String code, int weight) {
        return publish(code, weight, new TaskGrayCommand("NONE", null, null, null, null));
    }

    private long publish(String code, int weight, TaskGrayCommand gray) {
        return publish(code, weight, gray, null);
    }

    private long publishFiltered(String code, String expr) {
        return publish(code, 1, new TaskGrayCommand("NONE", null, null, null, null), expr);
    }

    private long publish(String code, int weight, TaskGrayCommand gray, String expr) {
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setStatus("PUBLISHED");
        entity.setVersion(1);
        entity.setCycleType("NONE");
        entity.setGrayType(gray.type());
        entity.setSortWeight(weight);
        entity.setDeleted(0);
        definitions.insert(entity);
        SnapshotContent content = new SnapshotContent(
                code,
                code,
                "desc",
                "daily",
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                weight,
                "NONE",
                null,
                null,
                null,
                null,
                gray,
                new TaskFilterCommand(expr, List.of(), List.of()),
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

    private long publishDaily(String code, TaskGrayCommand gray) {
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setStatus("PUBLISHED");
        entity.setVersion(1);
        entity.setCycleType("DAILY");
        entity.setGrayType(gray.type());
        entity.setSortWeight(1);
        entity.setDeleted(0);
        definitions.insert(entity);
        SnapshotContent content = new SnapshotContent(
                code,
                code,
                "desc",
                "daily",
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                1,
                "DAILY",
                null,
                null,
                null,
                null,
                gray,
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

    private TaskInstanceEntity inProgress(long taskId, long snapshotId) {
        return inProgressAt(taskId, snapshotId, "NONE");
    }

    private TaskInstanceEntity inProgressAt(long taskId, long snapshotId, String cycleKey) {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(taskId);
        row.setTaskCode("hidden");
        row.setVersion(1);
        row.setSnapshotId(snapshotId);
        row.setUserId(9L);
        row.setCycleKey(cycleKey);
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC));
        row.setStartedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        row.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return row;
    }

    private static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
