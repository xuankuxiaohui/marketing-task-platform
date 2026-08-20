package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.response.TaskStartResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** C-2 / R14.1: concurrent click/callback/progress converge to one completion. */
@Testcontainers
class StepAdvanceExactlyOnceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentClicksCompleteOnce() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = env.publishLegal("click_once");
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        env.tx.executeWithoutResult(
                                status -> env.steps.click(started.instanceId(), "click", 9L, "203.0.113.1", null, "WEB"));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            Integer completed = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_instance_step WHERE instance_id = ? AND step_code = 'click' AND status = 'COMPLETED'",
                    Integer.class,
                    started.instanceId());
            assertThat(completed).isEqualTo(1);
        }
    }

    @Test
    void concurrentCallbacksCompleteOnce() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = env.publishSteps(
                    "cb_once", List.of(new TaskStepCommand("cb", "回调", 1, "CALLBACK", null, null)), List.of());
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        env.tx.executeWithoutResult(status -> env.steps.callback(
                                new InternalCallbackCommand(started.instanceId(), null, null, null, "cb", "biz")));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = 'cb'",
                    String.class,
                    started.instanceId());
            assertThat(status).isEqualTo(StepStatuses.COMPLETED);
        }
    }

    @Test
    void concurrentDistinctReportIdsAccumulateOnceEach() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = env.publishSteps(
                    "pg_once", List.of(new TaskStepCommand("p", "进度", 1, "PROGRESS", 1000, null)), List.of());
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        env.tx.executeWithoutResult(status -> env.steps.progress(new InternalProgressCommand(
                                started.instanceId(), null, null, null, "p", 1, "r-" + idx)));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            Integer current = env.jdbc.queryForObject(
                    "SELECT progress_current FROM task_instance_step WHERE instance_id = ? AND step_code = 'p'",
                    Integer.class,
                    started.instanceId());
            assertThat(current).isEqualTo(n);
            Integer reports = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                    Integer.class,
                    started.instanceId());
            assertThat(reports).isEqualTo(n);
        }
    }
}
