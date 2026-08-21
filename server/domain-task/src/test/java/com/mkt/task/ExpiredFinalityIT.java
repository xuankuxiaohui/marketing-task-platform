package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R14.4: expired instances reject every advance entry and stay unchanged. */
@Testcontainers
class ExpiredFinalityIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void clickCallbackProgressRejectedAfterExpireAt() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            isolateInstances(env);
            long clickTask = env.publishLegal("exp_click");
            long cbTask = env.publishSteps(
                    "exp_cb", List.of(new TaskStepCommand("cb", "回调", 1, "CALLBACK", null, null)), List.of());
            long pgTask = env.publishSteps(
                    "exp_pg", List.of(new TaskStepCommand("p", "进度", 1, "PROGRESS", 10, null)), List.of());
            TaskStartResponse clickStarted =
                    env.tx.execute(status -> env.claims.start(clickTask, 9L, "203.0.113.1", null, "WEB"));
            TaskStartResponse cbStarted =
                    env.tx.execute(status -> env.claims.start(cbTask, 9L, "203.0.113.1", null, "WEB"));
            TaskStartResponse pgStarted =
                    env.tx.execute(status -> env.claims.start(pgTask, 9L, "203.0.113.1", null, "WEB"));
            LocalDateTime past = LocalDateTime.ofInstant(env.clock.instant().minusSeconds(1), java.time.ZoneOffset.UTC);
            env.jdbc.update(
                    "UPDATE task_instance SET expire_at = ? WHERE id IN (?, ?, ?)",
                    past,
                    clickStarted.instanceId(),
                    cbStarted.instanceId(),
                    pgStarted.instanceId());
            assertThatThrownBy(() -> env.tx.executeWithoutResult(
                            status -> env.steps.click(clickStarted.instanceId(), "click", 9L, "203.0.113.1", null, "WEB")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.steps.callback(
                            new InternalCallbackCommand(cbStarted.instanceId(), null, null, null, "cb", "biz"))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.steps.progress(
                            new InternalProgressCommand(pgStarted.instanceId(), null, null, null, "p", 1, "r1"))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
            String clickStatus = env.jdbc.queryForObject(
                    "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = 'click'",
                    String.class,
                    clickStarted.instanceId());
            String instanceStatus = env.jdbc.queryForObject(
                    "SELECT status FROM task_instance WHERE id = ?", String.class, clickStarted.instanceId());
            assertThat(clickStatus).isEqualTo("ACTIVE");
            assertThat(instanceStatus).isEqualTo(InstanceStatuses.IN_PROGRESS);
            Integer reports = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                    Integer.class,
                    pgStarted.instanceId());
            assertThat(reports).isEqualTo(0);
        }
    }

    @Test
    void schedulerFlipThenAllEntriesRejected() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            isolateInstances(env);
            long taskId = env.publishLegal("exp_sched");
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            LocalDateTime past = LocalDateTime.ofInstant(env.clock.instant().minusSeconds(1), java.time.ZoneOffset.UTC);
            env.jdbc.update("UPDATE task_instance SET expire_at = ? WHERE id = ?", past, started.instanceId());
            assertThat(env.instanceAdmin.expireDue()).isEqualTo(1);
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM task_instance WHERE id = ?", String.class, started.instanceId());
            assertThat(status).isEqualTo(InstanceStatuses.EXPIRED);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(
                            status1 -> env.steps.click(started.instanceId(), "click", 9L, "203.0.113.1", null, "WEB")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_instance WHERE id = ?", String.class, started.instanceId()))
                    .isEqualTo(InstanceStatuses.EXPIRED);
        }
    }

    @Test
    void expiredStatusRejectsClick() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            isolateInstances(env);
            long taskId = env.publishLegal("exp_status");
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            env.jdbc.update(
                    "UPDATE task_instance SET status = 'EXPIRED' WHERE id = ?", started.instanceId());
            assertThatThrownBy(() -> env.tx.executeWithoutResult(
                            status -> env.steps.click(started.instanceId(), "click", 9L, "203.0.113.1", null, "WEB")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(TaskErrorCodes.INSTANCE_EXPIRED);
        }
    }

    private static void isolateInstances(ClaimITSupport env) {
        env.jdbc.update("DELETE FROM task_progress_report");
        env.jdbc.update("DELETE FROM task_instance_step");
        env.jdbc.update("DELETE FROM task_instance");
    }
}
