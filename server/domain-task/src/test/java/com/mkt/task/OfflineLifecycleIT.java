package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R11.6 / R12 / R14.10：真实 MySQL 的下线、快照互斥与事务原子性回归。 */
@Testcontainers
class OfflineLifecycleIT {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Container
    // 锁等待断言读取 performance_schema，仅此隔离测试容器使用 root。
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("root")
            .withPassword("mkt");

    @Test
    void offlineDefinitionAndInstanceDeadlinesRollbackTogether() throws Exception {
        try (ClaimITSupport env = environment()) {
            long taskId = env.publish(configured(
                    PublishITSupport.legal(code("rollback")), null, null, NOW.plusSeconds(30L * 86400), null));
            TaskStartResponse started = start(env, taskId, 9L);
            LocalDateTime originalDeadline = deadline(env, started.instanceId());

            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> {
                        env.publishes.offline(taskId);
                        assertThat(definitionStatus(env, taskId)).isEqualTo("OFFLINE");
                        assertThat(deadline(env, started.instanceId())).isEqualTo(utc(NOW.plusSeconds(7L * 86400)));
                        throw new IllegalStateException("模拟下线事务后续失败");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("模拟下线事务后续失败");

            assertThat(definitionStatus(env, taskId)).isEqualTo("PUBLISHED");
            assertThat(env.jdbc.queryForObject(
                            "SELECT offline_at FROM task_definition WHERE id = ?", LocalDateTime.class, taskId))
                    .isNull();
            assertThat(deadline(env, started.instanceId())).isEqualTo(originalDeadline);
            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        }
    }

    @Test
    void deadlineReachedAfterStepCasRollsBackItsStepAndOutboxBeforeGrantingNextReward() throws Exception {
        EventPublisher advancing = mock(EventPublisher.class);
        try (ClaimITSupport env = new ClaimITSupport(
                MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), advancing)) {
            isolateInstances(env);
            Instant deadline = NOW.plusSeconds(1);
            EventPublisher actual = new EventPublisher(new JdbcOutboxStore(env.jdbc), OutboxProducer.PORTAL);
            doAnswer(invocation -> {
                        String eventCode = invocation.getArgument(0);
                        long eventId = actual.append(
                                eventCode, invocation.getArgument(1), invocation.getArgument(2),
                                invocation.getArgument(3));
                        if (EventCodes.TASK_STEP_COMPLETE.equals(eventCode)) {
                            env.clock.setInstant(deadline);
                        }
                        return eventId;
                    })
                    .when(advancing)
                    .append(any(), any(), any(), any());
            TaskDefinitionSaveCommand base = PublishITSupport.legal(code("cascade_deadline"));
            long taskId = env.publish(new TaskDefinitionSaveCommand(
                    base.id(), base.code(), base.name(), base.description(), base.category(), base.iconUrl(),
                    base.badgeText(), base.startTime(), base.endTime(), base.sortWeight(), base.cycleType(),
                    base.cronExpr(), base.specialStart(), base.specialEnd(), base.mutexGroupId(), base.gray(),
                    base.filter(), List.of(
                            new TaskStepCommand("a", "点击", 1, "CLICK", null, null),
                            new TaskStepCommand("r", "奖励", 2, "REWARD", null, 8L)),
                    List.of(new TaskTransitionCommand("a", "r", null, 0)), List.of()));
            TaskStartResponse started = start(env, taskId, 9L);
            env.jdbc.update("UPDATE task_instance SET expire_at = ? WHERE id = ?", utc(deadline), started.instanceId());

            assertThatThrownBy(() -> env.tx.executeWithoutResult(status ->
                            env.steps.click(started.instanceId(), "a", 9L, "203.0.113.1", null, "WEB")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);

            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = 'a'",
                            String.class, started.instanceId()))
                    .isEqualTo("ACTIVE");
            assertThat(env.jdbc.queryForObject(
                            "SELECT version FROM task_instance_step WHERE instance_id = ? AND step_code = 'a'",
                            Integer.class, started.instanceId()))
                    .isZero();
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = 'r'",
                            String.class, started.instanceId()))
                    .isEqualTo("INACTIVE");
            assertThat(env.rewards.calls).isEmpty();
            assertThat(eventCount(env, EventCodes.TASK_INSTANCE_START, started.instanceId())).isEqualTo(1);
            assertThat(eventCount(env, EventCodes.TASK_STEP_COMPLETE, started.instanceId())).isZero();
            assertThat(eventCount(env, EventCodes.TASK_INSTANCE_COMPLETE, started.instanceId())).isZero();
        }
    }

    @Test
    void snapshotMutexOccupancySurvivesOfflineAndGroupChangeUntilCompletion() throws Exception {
        try (ClaimITSupport env = environment()) {
            long groupA = insertGroup(env, code("group_a"));
            long groupB = insertGroup(env, code("group_b"));
            String ownerCode = code("owner");
            long owner = env.publish(configured(PublishITSupport.legal(ownerCode), null, null, null, groupA));
            TaskStartResponse started = start(env, owner, 9L);
            Long snapshotId = env.jdbc.queryForObject(
                    "SELECT snapshot_id FROM task_instance WHERE id = ?", Long.class, started.instanceId());
            String originalSnapshot = env.jdbc.queryForObject(
                    "SELECT content FROM task_version_snapshot WHERE id = ?", String.class, snapshotId);
            env.tx.executeWithoutResult(status -> env.publishes.offline(owner));
            LocalDateTime originalDeadline = deadline(env, started.instanceId());
            long peerA = env.publish(configured(PublishITSupport.legal(code("peer_a")), null, null, null, groupA));
            assertMutexBlocked(env, peerA, 9L);

            env.tx.executeWithoutResult(status -> env.defs.saveAggregate(
                    configured(PublishITSupport.legal(ownerCode), owner, null, null, groupB)));
            env.tx.executeWithoutResult(status -> env.publishes.publish(owner, new PublishCommand(null, null)));
            long peerB = env.publish(configured(PublishITSupport.legal(code("peer_b")), null, null, null, groupB));
            assertMutexBlocked(env, peerA, 9L);
            assertThat(start(env, peerB, 9L).instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(deadline(env, started.instanceId())).isEqualTo(originalDeadline);
            assertThat(env.jdbc.queryForObject(
                            "SELECT snapshot_id FROM task_instance WHERE id = ?", Long.class, started.instanceId()))
                    .isEqualTo(snapshotId);
            assertThat(env.jdbc.queryForObject(
                            "SELECT content FROM task_version_snapshot WHERE id = ?", String.class, snapshotId))
                    .isEqualTo(originalSnapshot);

            env.tx.executeWithoutResult(status ->
                    env.steps.click(started.instanceId(), "click", 9L, "203.0.113.1", null, "WEB"));
            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
            assertThat(start(env, peerA, 9L).instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(eventCount(env, EventCodes.TASK_INSTANCE_COMPLETE, started.instanceId())).isEqualTo(1);
        }
    }

    @Test
    void nullSnapshotMutexIsDistinctFromTheGroupWhoseCodeIsLiteralNull() throws Exception {
        try (ClaimITSupport env = environment()) {
            long groupId = insertGroup(env, "null");
            long independent = env.publishLegal(code("no_mutex"));
            assertThat(start(env, independent, 9L).instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
            long grouped = env.publish(configured(
                    PublishITSupport.legal(code("literal_null")), null, null, null, groupId));

            assertThat(start(env, grouped, 9L).instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);

            long peer = env.publish(configured(PublishITSupport.legal(code("null_peer")), null, null, null, groupId));
            assertMutexBlocked(env, peer, 9L);
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_instance WHERE user_id = 9", Long.class))
                    .isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLICK", "CALLBACK", "PROGRESS"})
    void deadlineRejectsActiveAdvanceAndScannerReleasesOfflineMutexOnce(String stepType) throws Exception {
        try (ClaimITSupport env = environment()) {
            long groupId = insertGroup(env, code("expiry_group"));
            TaskDefinitionSaveCommand base = configured(
                    PublishITSupport.legal(code("expiry_owner")), null, null, null, groupId);
            TaskDefinitionSaveCommand command = new TaskDefinitionSaveCommand(
                    base.id(), base.code(), base.name(), base.description(), base.category(), base.iconUrl(),
                    base.badgeText(), base.startTime(), base.endTime(), base.sortWeight(), base.cycleType(),
                    base.cronExpr(), base.specialStart(), base.specialEnd(), base.mutexGroupId(), base.gray(),
                    base.filter(), List.of(new TaskStepCommand(
                            "work", "执行", 1, stepType, "PROGRESS".equals(stepType) ? 1 : null, null)),
                    List.of(), List.of());
            long owner = env.publish(command);
            TaskStartResponse started = start(env, owner, 9L);
            env.tx.executeWithoutResult(status -> env.publishes.offline(owner));
            long peer = env.publish(configured(PublishITSupport.legal(code("expiry_peer")), null, null, null, groupId));
            assertMutexBlocked(env, peer, 9L);
            env.clock.setInstant(deadline(env, started.instanceId()).toInstant(ZoneOffset.UTC));
            ThrowingCallable advance = switch (stepType) {
                case "CLICK" -> () -> env.tx.executeWithoutResult(status ->
                        env.steps.click(started.instanceId(), "work", 9L, "203.0.113.1", null, "WEB"));
                case "CALLBACK" -> () -> env.tx.executeWithoutResult(status -> env.steps.callback(
                        new InternalCallbackCommand(started.instanceId(), null, null, null, "work", "due-biz")));
                case "PROGRESS" -> () -> env.tx.executeWithoutResult(status -> env.steps.progress(
                        new InternalProgressCommand(started.instanceId(), null, null, null, "work", 1, "due-report")));
                default -> throw new IllegalArgumentException(stepType);
            };

            assertThatThrownBy(advance)
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = 'work'",
                            String.class, started.instanceId()))
                    .isEqualTo("ACTIVE");
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                            Long.class, started.instanceId()))
                    .isZero();
            assertThat(env.instanceAdmin.expireDue()).isEqualTo(1);
            assertThat(env.instanceAdmin.expireDue()).isZero();
            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.EXPIRED);
            assertThat(eventCount(env, EventCodes.TASK_INSTANCE_EXPIRE, started.instanceId())).isEqualTo(1);
            assertThat(start(env, peer, 9L).instanceStatus()).isEqualTo(InstanceStatuses.IN_PROGRESS);
        }
    }

    @Test
    void deadlineCasChecksExpectedValueAndCannotReviveDueOrTerminalInstances() throws Exception {
        try (ClaimITSupport env = environment()) {
            long taskId = env.publishLegal(code("deadline_cas"));
            TaskStartResponse first = start(env, taskId, 9L);
            LocalDateTime previous = deadline(env, first.instanceId());
            LocalDateTime changed = utc(NOW.plusSeconds(3L * 86400));
            assertThat(env.instanceStore.updateExpireAtCas(first.instanceId(), previous, changed, utc(NOW)))
                    .isEqualTo(1);
            assertThat(env.instanceStore.updateExpireAtCas(
                            first.instanceId(), previous, utc(NOW.plusSeconds(86400)), utc(NOW)))
                    .isZero();
            assertThat(deadline(env, first.instanceId())).isEqualTo(changed);
            env.jdbc.update("UPDATE task_instance SET status = 'COMPLETED' WHERE id = ?", first.instanceId());
            assertThat(env.instanceStore.updateExpireAtCas(first.instanceId(), changed, previous, utc(NOW))).isZero();
            assertThat(instanceStatus(env, first.instanceId())).isEqualTo(InstanceStatuses.COMPLETED);
            assertThat(deadline(env, first.instanceId())).isEqualTo(changed);

            TaskStartResponse due = start(env, taskId, 10L);
            env.jdbc.update("UPDATE task_instance SET expire_at = ? WHERE id = ?", utc(NOW), due.instanceId());
            assertThat(env.instanceStore.updateExpireAtCas(due.instanceId(), utc(NOW), previous, utc(NOW))).isZero();
            assertThat(deadline(env, due.instanceId())).isEqualTo(utc(NOW));
            assertThat(instanceStatus(env, due.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        }
    }

    @Test
    void claimReadersDoNotBlockEachOtherAndOfflineWaitsToRecomputeBothCommittedInstances() throws Exception {
        CountDownLatch claimAdmitted = new CountDownLatch(2);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        UserAttributePort blockedUsers = new UserAttributePort() {
            @Override
            public UserAttributes attributes(long userId) {
                return ClaimITSupport.active();
            }

            @Override
            public UserAttributes lockAndGet(long userId) {
                claimAdmitted.countDown();
                awaitLatch(releaseClaim);
                return attributes(userId);
            }
        };
        try (ClaimITSupport env = new ClaimITSupport(MYSQL, blockedUsers, ClaimITSupport.passRisk(), null);
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            isolateInstances(env);
            long taskId = env.publish(configured(
                    PublishITSupport.legal(code("admitted_claim")), null, null, NOW.plusSeconds(30L * 86400), null));
            Future<TaskStartResponse> claim = pool.submit(() -> start(env, taskId, 9L));
            Future<TaskStartResponse> secondClaim = pool.submit(() -> start(env, taskId, 10L));
            Future<?> offline = null;
            try {
                assertThat(claimAdmitted.await(10, TimeUnit.SECONDS)).isTrue();
                offline = pool.submit(() -> env.tx.executeWithoutResult(status -> env.publishes.offline(taskId)));
                awaitDefinitionLockWait(env);
                assertThat(offline.isDone()).isFalse();
            } finally {
                releaseClaim.countDown();
            }
            TaskStartResponse started = claim.get(10, TimeUnit.SECONDS);
            TaskStartResponse secondStarted = secondClaim.get(10, TimeUnit.SECONDS);
            assertThat(offline).isNotNull();
            offline.get(10, TimeUnit.SECONDS);
            assertThat(definitionStatus(env, taskId)).isEqualTo("OFFLINE");
            assertThat(deadline(env, started.instanceId())).isEqualTo(utc(NOW.plusSeconds(7L * 86400)));
            assertThat(deadline(env, secondStarted.instanceId())).isEqualTo(utc(NOW.plusSeconds(7L * 86400)));
            assertThat(instanceStatus(env, started.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
            assertThat(instanceStatus(env, secondStarted.instanceId())).isEqualTo(InstanceStatuses.IN_PROGRESS);
        }
    }

    @Test
    void claimWaitingForOfflineWriteLockSeesCommittedOfflineAndDoesNotInsert() throws Exception {
        CountDownLatch offlineApplied = new CountDownLatch(1);
        CountDownLatch commitOffline = new CountDownLatch(1);
        try (ClaimITSupport env = environment();
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long taskId = env.publishLegal(code("write_lock"));
            Future<?> offline = pool.submit(() -> env.tx.executeWithoutResult(status -> {
                env.publishes.offline(taskId);
                offlineApplied.countDown();
                awaitLatch(commitOffline);
            }));
            Future<TaskStartResponse> claim = null;
            try {
                assertThat(offlineApplied.await(10, TimeUnit.SECONDS)).isTrue();
                claim = pool.submit(() -> start(env, taskId, 9L));
                awaitDefinitionLockWait(env);
                assertThat(claim.isDone()).isFalse();
            } finally {
                commitOffline.countDown();
            }
            offline.get(10, TimeUnit.SECONDS);
            assertThat(claim).isNotNull();
            Future<TaskStartResponse> waitingClaim = claim;
            assertThatThrownBy(() -> waitingClaim.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.CLAIM_NOT_VISIBLE);
            assertThat(definitionStatus(env, taskId)).isEqualTo("OFFLINE");
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_instance WHERE task_id = ?", Long.class, taskId))
                    .isZero();
        }
    }

    @Test
    void saveWaitingForOfflineWriteLockReadsCommittedStatusAndReturnsToDraft() throws Exception {
        CountDownLatch offlineApplied = new CountDownLatch(1);
        CountDownLatch commitOffline = new CountDownLatch(1);
        try (ClaimITSupport env = environment();
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            String taskCode = code("offline_save");
            long taskId = env.publishLegal(taskCode);
            Future<?> offline = pool.submit(() -> env.tx.executeWithoutResult(status -> {
                env.publishes.offline(taskId);
                offlineApplied.countDown();
                awaitLatch(commitOffline);
            }));
            Future<String> save = null;
            try {
                assertThat(offlineApplied.await(10, TimeUnit.SECONDS)).isTrue();
                save = pool.submit(() -> {
                    UserContext.set(new UserPrincipal(1L, "admin", "op"));
                    try {
                        return env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.renamed(
                                        PublishITSupport.legal(taskCode), taskId, "下线后修订"))
                                .status());
                    } finally {
                        UserContext.clear();
                    }
                });
                awaitDefinitionLockWait(env);
                assertThat(save.isDone()).isFalse();
            } finally {
                commitOffline.countDown();
            }
            offline.get(10, TimeUnit.SECONDS);
            assertThat(save).isNotNull();

            assertThat(save.get(10, TimeUnit.SECONDS)).isEqualTo("DRAFT");
            assertThat(definitionStatus(env, taskId)).isEqualTo("DRAFT");
            assertThat(env.jdbc.queryForObject(
                            "SELECT name FROM task_definition WHERE id = ?", String.class, taskId))
                    .isEqualTo("下线后修订");
            assertThat(env.jdbc.queryForObject(
                            "SELECT pending_revision FROM task_definition WHERE id = ?", Integer.class, taskId))
                    .isZero();
            assertThat(env.jdbc.queryForObject(
                            "SELECT offline_at FROM task_definition WHERE id = ?", LocalDateTime.class, taskId))
                    .isEqualTo(utc(NOW));
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?", Long.class, taskId))
                    .isEqualTo(1);
        }
    }

    private static ClaimITSupport environment() throws Exception {
        ClaimITSupport env = new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null);
        isolateInstances(env);
        return env;
    }

    private static void isolateInstances(ClaimITSupport env) {
        env.jdbc.update("DELETE FROM task_progress_report");
        env.jdbc.update("DELETE FROM task_instance_step");
        env.jdbc.update("DELETE FROM task_instance");
    }

    private static long insertGroup(ClaimITSupport env, String code) {
        env.jdbc.update("INSERT INTO task_mutex_group (code, name, cross_cycle) VALUES (?, ?, 0)", code, code);
        return env.jdbc.queryForObject("SELECT id FROM task_mutex_group WHERE code = ?", Long.class, code);
    }

    private static TaskStartResponse start(ClaimITSupport env, long taskId, long userId) {
        return env.tx.execute(status -> env.claims.start(taskId, userId, "203.0.113.1", null, "WEB"));
    }

    private static void assertMutexBlocked(ClaimITSupport env, long taskId, long userId) {
        assertThatThrownBy(() -> start(env, taskId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CLAIM_MUTEX_BLOCKED);
    }

    private static LocalDateTime deadline(ClaimITSupport env, long instanceId) {
        return env.jdbc.queryForObject(
                "SELECT expire_at FROM task_instance WHERE id = ?", LocalDateTime.class, instanceId);
    }

    private static String definitionStatus(ClaimITSupport env, long taskId) {
        return env.jdbc.queryForObject("SELECT status FROM task_definition WHERE id = ?", String.class, taskId);
    }

    private static String instanceStatus(ClaimITSupport env, long instanceId) {
        return env.jdbc.queryForObject("SELECT status FROM task_instance WHERE id = ?", String.class, instanceId);
    }

    private static long eventCount(ClaimITSupport env, String eventCode, long instanceId) {
        return env.jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM sys_outbox
                WHERE event_code = ? AND aggregate_type = 'task_instance' AND aggregate_id = ?
                """,
                Long.class, eventCode, String.valueOf(instanceId));
    }

    private static void awaitDefinitionLockWait(ClaimITSupport env) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(env.jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM performance_schema.data_lock_waits waits
                        JOIN performance_schema.data_locks requested
                          ON requested.ENGINE = waits.ENGINE
                         AND requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
                        WHERE requested.OBJECT_SCHEMA = DATABASE()
                          AND requested.OBJECT_NAME = 'task_definition'
                        """, Long.class))
                .isPositive());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试事务等待释放超时");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试事务等待被中断", interrupted);
        }
    }

    private static TaskDefinitionSaveCommand configured(
            TaskDefinitionSaveCommand base, Long id, String cycleType, Instant endTime, Long mutexGroupId) {
        return new TaskDefinitionSaveCommand(
                id, base.code(), base.name(), base.description(), base.category(), base.iconUrl(), base.badgeText(),
                base.startTime(), endTime, base.sortWeight(), cycleType == null ? base.cycleType() : cycleType,
                base.cronExpr(), base.specialStart(), base.specialEnd(), mutexGroupId, base.gray(), base.filter(),
                base.steps(), base.transitions(), base.actions());
    }

    private static String code(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
