package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.response.TaskProgressResponse;
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

/** C-3 / R14.2: same reportId accumulates once; duplicates are 200. */
@Testcontainers
class ProgressDedupIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void mixedDuplicateAndUniqueReportIds() throws Exception {
        try (ClaimITSupport env =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = env.publishSteps(
                    "pg_dedup", List.of(new TaskStepCommand("p", "进度", 1, "PROGRESS", 1000, null)), List.of());
            TaskStartResponse started =
                    env.tx.execute(status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            int unique = 34;
            int dupTimes = 30;
            int n = unique + dupTimes;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            List<TaskProgressResponse> responses = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < unique; i++) {
                int idx = i;
                pool.submit(() -> run(env, start, done, errors, responses, started.instanceId(), "u-" + idx));
            }
            for (int i = 0; i < dupTimes; i++) {
                pool.submit(() -> run(env, start, done, errors, responses, started.instanceId(), "dup"));
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            assertThat(responses).hasSize(n);
            Integer current = env.jdbc.queryForObject(
                    "SELECT progress_current FROM task_instance_step WHERE instance_id = ? AND step_code = 'p'",
                    Integer.class,
                    started.instanceId());
            assertThat(current).isEqualTo(unique + 1);
            Integer reports = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_progress_report WHERE instance_id = ?",
                    Integer.class,
                    started.instanceId());
            assertThat(reports).isEqualTo(unique + 1);
        }
    }

    private static void run(
            ClaimITSupport env,
            CountDownLatch start,
            CountDownLatch done,
            List<Throwable> errors,
            List<TaskProgressResponse> responses,
            long instanceId,
            String reportId) {
        try {
            start.await();
            responses.add(env.tx.execute(status -> env.steps.progress(
                    new InternalProgressCommand(instanceId, null, null, null, "p", 1, reportId))));
        } catch (Throwable ex) {
            errors.add(ex);
        } finally {
            done.countDown();
        }
    }
}
