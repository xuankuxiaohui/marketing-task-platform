package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.time.MutableClock;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
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
import com.mkt.task.testsupport.MemoryTaskInstanceStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import com.mkt.task.testsupport.MemoryTaskVersionSnapshotStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TaskPublishAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryTaskDefinitionStore definitions;
    private MemoryTaskChildStore children;
    private MemoryTaskVersionSnapshotStore snapshots;
    private MemoryPrizeEnabledLookup prizes;
    private CapturingAudit audits;
    private MemoryTaskInstanceStore instances;
    private TaskSettings settings;
    private MutableClock clock;
    private TaskDefinitionAppService defs;
    private TaskPublishAppService publishes;

    @BeforeEach
    void setUp() {
        definitions = new MemoryTaskDefinitionStore();
        children = new MemoryTaskChildStore();
        snapshots = new MemoryTaskVersionSnapshotStore();
        prizes = new MemoryPrizeEnabledLookup();
        audits = new CapturingAudit();
        instances = new MemoryTaskInstanceStore(snapshots);
        settings = new TaskSettings();
        settings.setStepMaxCount(10);
        clock = new MutableClock(NOW);
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
                instances,
                settings,
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
    void offlineRecomputesWindowDeadlineFromBoundSnapshotAndCurrentSetting() {
        long id = defs.saveAggregate(withTiming(legal("offline_window"), "NONE", NOW.plusSeconds(30L * 86400))).id();
        publishes.publish(id, new PublishCommand(null, null));
        TaskInstanceEntity row = insertInstance(id, 9L, NOW, NOW.plusSeconds(37L * 86400));
        defs.saveAggregate(withName(withTiming(legal("offline_window"), "NONE", NOW.minusSeconds(86400)), id, "草稿窗"));
        settings.setExpireAfterWindowDays(3);

        publishes.offline(id);

        assertThat(row.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(3L * 86400), ZoneOffset.UTC));
        assertThat(row.getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(row.getSnapshotId()).isEqualTo(snapshots.getByTaskAndVersion(id, 1).getId());
        assertThat(definitions.getById(id).getOfflineAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void offlineUsesOriginalStartedAtCycleWhenInstanceIsFromYesterday() {
        long id = defs.saveAggregate(withTiming(legal("offline_daily"), "DAILY", null)).id();
        publishes.publish(id, new PublishCommand(null, null));
        Instant startedAt = NOW.minusSeconds(86400);
        TaskInstanceEntity row = insertInstance(id, 9L, startedAt, NOW.plusSeconds(30L * 86400), "20260818");

        publishes.offline(id);

        Instant originalCycleEnd = Instant.parse("2026-08-18T15:59:59.999Z");
        assertThat(row.getExpireAt())
                .isEqualTo(LocalDateTime.ofInstant(originalCycleEnd.plusSeconds(7L * 86400), ZoneOffset.UTC));
        assertThat(row.getCycleKey()).isEqualTo("20260818");
    }

    @Test
    void offlineMayExtendUnboundedOnceInstanceThatHasNotExpired() {
        long id = defs.saveAggregate(legal("offline_once")).id();
        publishes.publish(id, new PublishCommand(null, null));
        TaskInstanceEntity row = insertInstance(id, 9L, NOW.minusSeconds(86400), NOW.plusSeconds(6L * 86400));

        publishes.offline(id);

        assertThat(row.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(7L * 86400), ZoneOffset.UTC));
        assertThat(row.getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
    }

    @Test
    void offlineDoesNotResurrectDueInstancesOrChangeTerminalAndOtherTaskDeadlines() {
        long id = defs.saveAggregate(legal("offline_preserve")).id();
        publishes.publish(id, new PublishCommand(null, null));
        TaskInstanceEntity due = insertInstance(id, 9L, NOW.minusSeconds(86400), NOW);
        TaskInstanceEntity expiredEarlier = insertInstance(id, 10L, NOW.minusSeconds(86400), NOW.minusMillis(1));
        TaskInstanceEntity completed = insertInstance(id, 11L, NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        completed.setStatus(InstanceStatuses.COMPLETED);
        TaskInstanceEntity abandoned = insertInstance(id, 12L, NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        abandoned.setStatus(InstanceStatuses.ABANDONED);
        TaskInstanceEntity expired = insertInstance(id, 13L, NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        expired.setStatus(InstanceStatuses.EXPIRED);
        long unrelatedId = defs.saveAggregate(legal("offline_unrelated")).id();
        publishes.publish(unrelatedId, new PublishCommand(null, null));
        TaskInstanceEntity unrelated = insertInstance(unrelatedId, 9L, NOW, NOW.plusSeconds(86400));

        publishes.offline(id);

        assertThat(due.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(expiredEarlier.getExpireAt()).isEqualTo(LocalDateTime.ofInstant(NOW.minusMillis(1), ZoneOffset.UTC));
        assertThat(due.getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(expiredEarlier.getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(List.of(completed, abandoned, expired, unrelated))
                .allSatisfy(row -> assertThat(row.getExpireAt())
                        .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC)));
        assertThat(completed.getStatus()).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(abandoned.getStatus()).isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(expired.getStatus()).isEqualTo(InstanceStatuses.EXPIRED);
        assertThat(definitions.getById(unrelatedId).getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void republishKeepsOldInstanceDeadlineAndNewClaimDoesNotInheritPastOfflineTime() {
        long id = defs.saveAggregate(
                withTiming(legal("republish_deadline"), "NONE", NOW.plusSeconds(30L * 86400))).id();
        publishes.publish(id, new PublishCommand(null, null));
        TaskInstanceEntity original = insertInstance(id, 9L, NOW, NOW.plusSeconds(37L * 86400));
        publishes.offline(id);
        LocalDateTime oldDeadline = original.getExpireAt();
        Long boundSnapshot = original.getSnapshotId();
        clock.setInstant(NOW.plusSeconds(86400));
        defs.saveAggregate(withName(
                withTiming(legal("republish_deadline"), "NONE", NOW.plusSeconds(40L * 86400)), id, "重发"));
        publishes.publish(id, new PublishCommand(null, null));
        TaskClaimAppService claims = new TaskClaimAppService(
                definitions, snapshots, instances, new MemoryTaskCrowdStore(), new MemoryTaskMutexGroupStore(),
                activeUsers(), null, null, clock, settings);

        var created = claims.start(id, 10L, "203.0.113.1", null, "WEB");

        assertThat(original.getExpireAt()).isEqualTo(oldDeadline);
        assertThat(original.getSnapshotId()).isEqualTo(boundSnapshot);
        assertThat(original.getVersion()).isEqualTo(1);
        assertThat(instances.getById(created.instanceId()).getVersion()).isEqualTo(2);
        assertThat(instances.getById(created.instanceId()).getExpireAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(47L * 86400), ZoneOffset.UTC));
        assertThat(definitions.getById(id).getOfflineAt()).isNull();
    }

    @Test
    void offlineRecomputesEveryInFlightDeadlineAcrossMoreThanOneBatch() {
        long id = defs.saveAggregate(legal("offline_many")).id();
        publishes.publish(id, new PublishCommand(null, null));
        List<TaskInstanceEntity> rows = new ArrayList<>(105);
        for (long userId = 100L; userId < 205L; userId++) {
            rows.add(insertInstance(id, userId, NOW.minusSeconds(86400), NOW.plusSeconds(6L * 86400)));
        }

        publishes.offline(id);

        assertThat(rows).hasSize(105).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(row.getExpireAt())
                    .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(7L * 86400), ZoneOffset.UTC));
        });
    }

    @Test
    void secondOfflineRecomputesEachVersionFromItsOwnBoundWindowEnd() {
        String code = "offline_twice";
        long id = defs.saveAggregate(withTiming(legal(code), "NONE", NOW.plusSeconds(86400))).id();
        publishes.publish(id, new PublishCommand(null, null));
        TaskInstanceEntity original = insertInstance(id, 9L, NOW, NOW.plusSeconds(8L * 86400));
        publishes.offline(id);
        assertThat(original.getExpireAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(7L * 86400), ZoneOffset.UTC));
        clock.setInstant(NOW.plusSeconds(2L * 86400));
        defs.saveAggregate(withName(withTiming(legal(code), "NONE", NOW.plusSeconds(30L * 86400)), id, "新窗口"));
        publishes.publish(id, new PublishCommand(null, null));
        TaskClaimAppService claims = new TaskClaimAppService(
                definitions, snapshots, instances, new MemoryTaskCrowdStore(), new MemoryTaskMutexGroupStore(),
                activeUsers(), null, null, clock, settings);
        var current = claims.start(id, 10L, "203.0.113.1", null, "WEB");
        Long originalSnapshotId = original.getSnapshotId();

        publishes.offline(id);

        assertThat(original.getVersion()).isEqualTo(1);
        assertThat(original.getSnapshotId()).isEqualTo(originalSnapshotId);
        assertThat(original.getExpireAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(8L * 86400), ZoneOffset.UTC));
        assertThat(instances.getById(current.instanceId()).getVersion()).isEqualTo(2);
        assertThat(instances.getById(current.instanceId()).getExpireAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(9L * 86400), ZoneOffset.UTC));
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
        definitions.getById(mutexA).setCycleType("MONTHLY");
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


    @Test
    void freezeToPublishedDoesNotPublishSnapshotCacheUntilCommit() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(store);
        publishes = new TaskPublishAppService(
                definitions,
                new MemoryTaskMutexGroupStore(),
                snapshots,
                defs,
                prizes,
                instances,
                settings,
                clock,
                cache,
                audits,
                new AlertWebhook(""),
                null,
                null);

        long id = defs.saveAggregate(legal("cache_after_commit")).id();
        String key = id + ":1";
        String redisKey = CacheNamespace.TASK_SNAPSHOT.redisKey(key);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            PublishResponse result = publishes.publish(id, new PublishCommand(null, null));
            assertThat(result.status()).isEqualTo("PUBLISHED");
            assertThat(result.version()).isEqualTo(1);
            assertThat(snapshots.listByTaskId(id)).hasSize(1);
            // F02 regression: shared snapshot must not be readable before commit
            assertThat(store.get(redisKey)).isNull();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(store.get(redisKey)).isNotNull();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void freezeToPublishedSkipsSnapshotCacheWhenTransactionRollsBack() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(store);
        publishes = new TaskPublishAppService(
                definitions,
                new MemoryTaskMutexGroupStore(),
                snapshots,
                defs,
                prizes,
                instances,
                settings,
                clock,
                cache,
                audits,
                new AlertWebhook(""),
                null,
                null);

        long id = defs.saveAggregate(legal("cache_on_rollback")).id();
        String redisKey = CacheNamespace.TASK_SNAPSHOT.redisKey(id + ":1");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            publishes.publish(id, new PublishCommand(null, null));
            assertThat(store.get(redisKey)).isNull();
            // Simulate rollback: Spring skips afterCommit callbacks
            TransactionSynchronizationManager.clear();
            assertThat(store.get(redisKey)).isNull();
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clear();
            }
        }
    }

    private static TaskDefinitionSaveCommand legal(String code) {
        return withSteps(
                code,
                List.of(
                        new TaskStepCommand("go_page", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go_page", "click", null, 0)));
    }

    private TaskInstanceEntity insertInstance(long taskId, long userId, Instant startedAt, Instant expireAt) {
        return insertInstance(taskId, userId, startedAt, expireAt, "NONE");
    }

    private TaskInstanceEntity insertInstance(
            long taskId, long userId, Instant startedAt, Instant expireAt, String cycleKey) {
        TaskInstanceEntity row = new TaskInstanceEntity();
        row.setTaskId(taskId);
        row.setTaskCode(definitions.getById(taskId).getCode());
        row.setVersion(1);
        row.setSnapshotId(snapshots.getByTaskAndVersion(taskId, 1).getId());
        row.setUserId(userId);
        row.setCycleKey(cycleKey);
        row.setStatus(InstanceStatuses.IN_PROGRESS);
        row.setStartedAt(LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC));
        row.setCreatedAt(LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC));
        row.setExpireAt(LocalDateTime.ofInstant(expireAt, ZoneOffset.UTC));
        row.setSimulated(0);
        instances.insert(row);
        return row;
    }

    private static TaskDefinitionSaveCommand withTiming(
            TaskDefinitionSaveCommand base, String cycleType, Instant endTime) {
        return new TaskDefinitionSaveCommand(
                base.id(), base.code(), base.name(), base.description(), base.category(), base.iconUrl(),
                base.badgeText(), base.startTime(), endTime, base.sortWeight(), cycleType, base.cronExpr(),
                base.specialStart(), base.specialEnd(), base.mutexGroupId(), base.gray(), base.filter(),
                base.steps(), base.transitions(), base.actions());
    }

    private static UserAttributePort activeUsers() {
        return new UserAttributePort() {
            @Override
            public UserAttributes attributes(long userId) {
                return new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.ACTIVE);
            }

            @Override
            public UserAttributes lockAndGet(long userId) {
                return attributes(userId);
            }
        };
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
