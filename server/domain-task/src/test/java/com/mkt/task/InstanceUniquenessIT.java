package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.response.TaskStartResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** C-1 / R13.1: concurrent start of the same (user, task, cycleKey) yields one instance. */
@Testcontainers
class InstanceUniquenessIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentStartsCollapseToOneInstance() throws Exception {
        try (ClaimITSupport env = new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = env.publishLegal("uniq_claim");
            int n = 128;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<TaskStartResponse> responses = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        responses.add(env.tx.execute(
                                status -> env.claims.start(taskId, 9L, "203.0.113.1", null, "WEB")));
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
            assertThat(responses).hasSize(n);
            Set<Long> ids = new HashSet<>();
            for (TaskStartResponse response : responses) {
                assertThat(response.instanceId()).isPositive();
                ids.add(response.instanceId());
            }
            assertThat(ids).hasSize(1);
            Integer count = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_instance WHERE user_id = 9 AND task_id = ?", Integer.class, taskId);
            assertThat(count).isEqualTo(1);
        }
    }
}
