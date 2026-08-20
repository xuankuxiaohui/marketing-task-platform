package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishCheckResponse;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryPrizeEnabledLookup;
import com.mkt.task.testsupport.MemoryTaskChildStore;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskPublishAppServiceTest {

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskChildStore children;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryPrizeEnabledLookup prizes;
    private CapturingAudit audits;
    private TaskDefinitionAppService defs;
    private TaskPublishAppService publishes;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        children = new MemoryTaskChildStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        prizes = new MemoryPrizeEnabledLookup();
        audits = new CapturingAudit();
        TaskSettings settings = new TaskSettings();
        settings.setStepMaxCount(10);
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        defs = new TaskDefinitionAppService(
                definitions,
                children,
                new MemoryTaskMutexGroupStore(),
                new MemoryTaskCrowdStore(),
                settings,
                clock);
        publishes = new TaskPublishAppService(
                definitions,
                new MemoryTaskMutexGroupStore(),
                snapshots,
                defs,
                prizes,
                clock,
                null,
                audits,
                new AlertWebhook(""),
                null,
                null);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void draftPublishCreatesSnapshotAndBumpsVersion() {
        long id = defs.saveAggregate(legal("pub_now")).id();
        PublishResponse result = publishes.publish(id, new PublishCommand(null, null));
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.requiresConfirm()).isFalse();
        assertThat(snapshots.listByTaskId(id)).hasSize(1);
        assertThat(definitions.getById(id).getPendingRevision()).isZero();
    }

    @Test
    void publishedRevisionRequiresConfirmThenSnapshots() {
        long id = defs.saveAggregate(legal("rev_task")).id();
        publishes.publish(id, new PublishCommand(null, null));
        defs.saveAggregate(withName(legal("rev_task"), id, "修订名"));
        assertThat(definitions.getById(id).getPendingRevision()).isEqualTo(1);
        assertThat(definitions.getById(id).getStatus()).isEqualTo("PUBLISHED");

        PublishResponse preview = publishes.publish(id, new PublishCommand(false, null));
        assertThat(preview.requiresConfirm()).isTrue();
        assertThat(preview.message()).contains("存量实例不受影响");
        assertThat(definitions.getById(id).getVersion()).isEqualTo(1);
        assertThat(snapshots.listByTaskId(id)).hasSize(1);

        PublishResponse done = publishes.publish(id, new PublishCommand(true, null));
        assertThat(done.requiresConfirm()).isFalse();
        assertThat(done.version()).isEqualTo(2);
        assertThat(snapshots.listByTaskId(id)).hasSize(2);
        assertThat(definitions.getById(id).getPendingRevision()).isZero();
    }

    @Test
    void scheduledRevisionDoesNotBumpVersionOrSnapshot() {
        long id = defs.saveAggregate(legal("sched_rev")).id();
        publishes.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z")));
        defs.saveAggregate(withName(legal("sched_rev"), id, "定时修订"));
        assertThat(definitions.getById(id).getStatus()).isEqualTo("SCHEDULED");
        assertThat(definitions.getById(id).getPendingRevision()).isEqualTo(1);

        PublishResponse result = publishes.publish(id, new PublishCommand(true, false));
        assertThat(result.status()).isEqualTo("SCHEDULED");
        assertThat(result.version()).isZero();
        assertThat(snapshots.listByTaskId(id)).isEmpty();
        assertThat(definitions.getById(id).getPendingRevision()).isZero();
    }

    @Test
    void scheduledEarlyPublishesSnapshot() {
        long id = defs.saveAggregate(legal("sched_early")).id();
        publishes.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z")));
        PublishResponse result = publishes.publish(id, new PublishCommand(null, true));
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.version()).isEqualTo(1);
        assertThat(snapshots.listByTaskId(id)).hasSize(1);
        assertThat(definitions.getById(id).getSchedulePublishAt()).isNull();
    }

    @Test
    void cancelScheduleReturnsDraft() {
        long id = defs.saveAggregate(legal("cancel_me")).id();
        publishes.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z")));
        PublishResponse result = publishes.cancelSchedule(id);
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(definitions.getById(id).getSchedulePublishAt()).isNull();
    }

    @Test
    void offlineThenSaveBecomesDraft() {
        long id = defs.saveAggregate(legal("off_task")).id();
        publishes.publish(id, new PublishCommand(null, null));
        publishes.offline(id);
        assertThat(definitions.getById(id).getStatus()).isEqualTo("OFFLINE");
        defs.saveAggregate(withName(legal("off_task"), id, "再编辑"));
        assertThat(definitions.getById(id).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void resetRevisionRestoresSnapshot() {
        long id = defs.saveAggregate(legal("reset_me")).id();
        publishes.publish(id, new PublishCommand(null, null));
        defs.saveAggregate(withName(legal("reset_me"), id, "脏修订"));
        assertThat(defs.get(id).name()).isEqualTo("脏修订");
        publishes.resetRevision(id);
        assertThat(defs.get(id).name()).isEqualTo("每日签到");
        assertThat(definitions.getById(id).getPendingRevision()).isZero();
        assertThat(definitions.getById(id).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishRejectsEmptyStepsWithCheckErrors() {
        TaskDefinitionSaveCommand empty = new TaskDefinitionSaveCommand(
                null,
                "empty_steps",
                "空",
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
        long id = defs.saveAggregate(empty).id();
        assertThatThrownBy(() -> publishes.publish(id, new PublishCommand(null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.errorCode()).isEqualTo(TaskErrorCodes.PUBLISH_VALIDATE_FAILED);
                    PublishCheckResponse data = (PublishCheckResponse) be.data();
                    assertThat(data.checkErrors()).extracting(e -> e.item()).contains("steps");
                });
        assertThat(definitions.getById(id).getStatus()).isEqualTo("DRAFT");
        assertThat(snapshots.listByTaskId(id)).isEmpty();
    }

    @Test
    void rewardPrizeMustBeEnabled() {
        prizes.allowAll = false;
        TaskDefinitionSaveCommand cmd = withSteps(
                "need_prize",
                List.of(new TaskStepCommand("pay", "奖", 1, "REWARD", null, 99L)),
                List.of());
        long id = defs.saveAggregate(cmd).id();
        assertThatThrownBy(() -> publishes.publish(id, new PublishCommand(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.PUBLISH_VALIDATE_FAILED);
    }

    @Test
    void batchPublishIsPerTaskAtomic() {
        long good1 = defs.saveAggregate(legal("batch_ok1")).id();
        long empty = defs.saveAggregate(emptySteps("batch_empty")).id();
        long expr = defs.saveAggregate(legal("batch_expr")).id();
        definitions.getById(expr).setFilterExpr("eval(1)");
        long window = defs.saveAggregate(legal("batch_window")).id();
        definitions.getById(window).setStartTime(java.time.LocalDateTime.of(2026, 8, 20, 0, 0));
        definitions.getById(window).setEndTime(java.time.LocalDateTime.of(2026, 8, 19, 0, 0));
        long mutexA = defs.saveAggregate(legal("batch_mx_a")).id();
        long mutexB = defs.saveAggregate(legal("batch_mx_b")).id();
        definitions.getById(mutexA).setMutexGroupId(9L);
        definitions.getById(mutexA).setCycleType("WEEKLY");
        definitions.getById(mutexB).setMutexGroupId(9L);
        definitions.getById(mutexB).setCycleType("DAILY");
        long late = defs.saveAggregate(legal("batch_late")).id();
        definitions.getById(late).setEndTime(java.time.LocalDateTime.of(2026, 8, 21, 0, 0));
        definitions.getById(late).setSchedulePublishAt(java.time.LocalDateTime.of(2026, 8, 22, 0, 0));
        long good2 = defs.saveAggregate(legal("batch_ok2")).id();
        long good3 = defs.saveAggregate(legal("batch_ok3")).id();
        long good4 = defs.saveAggregate(legal("batch_ok4")).id();
        long good5 = defs.saveAggregate(legal("batch_ok5")).id();

        List<Long> ids = List.of(good1, empty, expr, window, mutexB, late, good2, good3, good4, good5);
        List<BatchItemResponse> results = publishes.batchPublish(ids);
        assertThat(results).hasSize(10);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(2).success()).isFalse();
        assertThat(results.get(3).success()).isFalse();
        assertThat(results.get(4).success()).isFalse();
        assertThat(results.get(5).success()).isFalse();
        assertThat(results.subList(6, 10)).allMatch(BatchItemResponse::success);
        assertThat(definitions.getById(empty).getStatus()).isEqualTo("DRAFT");
        assertThat(definitions.getById(expr).getVersion()).isZero();
        assertThat(snapshots.listByTaskId(window)).isEmpty();
        assertThat(snapshots.listByTaskId(mutexB)).isEmpty();
        assertThat(snapshots.listByTaskId(late)).isEmpty();
        assertThat(definitions.getById(good5).getStatus()).isEqualTo("PUBLISHED");
        assertThat(definitions.getById(good5).getVersion()).isEqualTo(1);
    }

    @Test
    void versionsDiffDetectsStepChange() {
        long id = defs.saveAggregate(legal("diff_me")).id();
        publishes.publish(id, new PublishCommand(null, null));
        defs.saveAggregate(withSteps(
                "diff_me",
                id,
                "每日签到",
                List.of(
                        new TaskStepCommand("go_page", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null),
                        new TaskStepCommand("extra", "额外", 3, "CLICK", null, null)),
                List.of(
                        new TaskTransitionCommand("go_page", "click", null, 0),
                        new TaskTransitionCommand("click", "extra", null, 0))));
        publishes.publish(id, new PublishCommand(true, null));
        var diff = publishes.diff(id, 1, 2);
        assertThat(diff.steps()).anyMatch(e -> "ADD".equals(e.op()) && "extra".equals(e.key()));
        assertThat(publishes.versions(id)).extracting(v -> v.version()).containsExactly(2, 1);
    }

    @Test
    void scanDuePublishesAndFailureStaysScheduled() {
        long ok = defs.saveAggregate(legal("due_ok")).id();
        publishes.schedule(ok, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z")));
        definitions.getById(ok).setSchedulePublishAt(java.time.LocalDateTime.of(2026, 8, 18, 0, 0));

        long fail = defs.saveAggregate(disconnected("due_bad")).id();
        var failRow = definitions.getById(fail);
        failRow.setStatus("SCHEDULED");
        failRow.setSchedulePublishAt(java.time.LocalDateTime.of(2026, 8, 18, 0, 0));
        definitions.update(failRow);

        int published = publishes.scanDue();
        assertThat(published).isEqualTo(1);
        assertThat(definitions.getById(ok).getStatus()).isEqualTo("PUBLISHED");
        assertThat(definitions.getById(ok).getVersion()).isEqualTo(1);
        assertThat(definitions.getById(fail).getStatus()).isEqualTo("SCHEDULED");
        assertThat(definitions.getById(fail).getVersion()).isZero();
        assertThat(snapshots.listByTaskId(fail)).isEmpty();
        assertThat(audits.reasons).isNotEmpty();
        assertThat(audits.checkErrors).isNotEmpty();
        assertThat(audits.checkErrors.get(0)).extracting(e -> e.item()).contains("reachability");
        assertThat(audits.checkErrors.get(0))
                .extracting(e -> e.reason())
                .contains("存在不可达或死锁步骤");
        int again = publishes.scanDue();
        assertThat(again).isZero();
        assertThat(audits.reasons).hasSize(1);
    }

    @Test
    void scheduledPendingPreviewUsesScheduledHint() {
        long id = defs.saveAggregate(legal("sched_hint")).id();
        publishes.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z")));
        defs.saveAggregate(withName(legal("sched_hint"), id, "定时修订"));
        PublishResponse preview = publishes.publish(id, new PublishCommand(false, null));
        assertThat(preview.requiresConfirm()).isTrue();
        assertThat(preview.message()).isEqualTo(PublishResponse.SCHEDULED_REVISION_HINT);
        assertThat(preview.message()).doesNotContain("存量实例");
    }

    @Test
    void scheduleFailureViewReadsCheckErrorsFromAuditSummary() {
        String summary = JsonUtil.toJson(Map.of(
                "taskId",
                9,
                "code",
                "due_bad",
                "reason",
                "发布校验失败",
                "checkErrors",
                List.of(Map.of("item", "reachability", "reason", "存在不可达或死锁步骤"))));
        var view = TaskPublishAppService.failureView(
                1L, summary, "发布校验失败", Timestamp.from(Instant.parse("2026-08-19T00:00:00Z")));
        assertThat(view.taskId()).isEqualTo(9L);
        assertThat(view.taskCode()).isEqualTo("due_bad");
        assertThat(view.reason()).contains("reachability");
        assertThat(view.reason()).contains("存在不可达或死锁步骤");
        assertThat(view.reason()).isNotEqualTo("发布校验失败");
    }

    @Test
    void illegalTransitionsRejected() {
        long id = defs.saveAggregate(legal("illegal")).id();
        assertThatThrownBy(() -> publishes.offline(id))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> publishes.cancelSchedule(id))
                .isInstanceOf(BusinessException.class);
        publishes.publish(id, new PublishCommand(null, null));
        assertThatThrownBy(() -> publishes.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z"))))
                .isInstanceOf(BusinessException.class);
    }

    private static TaskDefinitionSaveCommand legal(String code) {
        return withSteps(
                code,
                List.of(
                        new TaskStepCommand("go_page", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go_page", "click", null, 0)));
    }

    private static TaskDefinitionSaveCommand emptySteps(String code) {
        return withSteps(code, List.of(), List.of());
    }

    private static TaskDefinitionSaveCommand disconnected(String code) {
        return withSteps(
                code,
                List.of(
                        new TaskStepCommand("a", "a", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "b", 2, "CLICK", null, null)),
                List.of());
    }

    private static TaskDefinitionSaveCommand withName(TaskDefinitionSaveCommand base, long id, String name) {
        return new TaskDefinitionSaveCommand(
                id,
                base.code(),
                name,
                base.description(),
                base.category(),
                base.iconUrl(),
                base.badgeText(),
                base.startTime(),
                base.endTime(),
                base.sortWeight(),
                base.cycleType(),
                base.cronExpr(),
                base.specialStart(),
                base.specialEnd(),
                base.mutexGroupId(),
                base.gray(),
                base.filter(),
                base.steps(),
                base.transitions(),
                base.actions());
    }

    private static TaskDefinitionSaveCommand withSteps(
            String code, List<TaskStepCommand> steps, List<TaskTransitionCommand> edges) {
        return withSteps(code, null, "每日签到", steps, edges);
    }

    private static TaskDefinitionSaveCommand withSteps(
            String code,
            Long id,
            String name,
            List<TaskStepCommand> steps,
            List<TaskTransitionCommand> edges) {
        return new TaskDefinitionSaveCommand(
                id,
                code,
                name,
                null,
                "daily",
                null,
                null,
                null,
                null,
                0,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                steps,
                edges,
                List.of());
    }

    private static final class CapturingAudit extends TaskAuditAppender {
        private final List<String> reasons = new ArrayList<>();
        private final List<List<PublishCheckError>> checkErrors = new ArrayList<>();

        private CapturingAudit() {
            super(null);
        }

        @Override
        public void schedulePublishFailure(long taskId, String code, String reason) {
            schedulePublishFailure(taskId, code, reason, List.of());
        }

        @Override
        public void schedulePublishFailure(
                long taskId, String code, String reason, List<PublishCheckError> errors) {
            reasons.add(taskId + ":" + reason);
            checkErrors.add(errors == null ? List.of() : List.copyOf(errors));
        }
    }
}
