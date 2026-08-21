package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Topology B: two admin-app scheduler instances. Scenario 24 = timed publish exactly-once
 * (feasibility §2 last row / design §7.5). Requires Docker; leave for CI when absent.
 */
@Testcontainers
class TwoAdminAppIT {

    static final Map<Integer, String> EXPECTED = Map.of(24, "恰一执行");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Test
    void mappingCoversScenario24AndForbids25() {
        assertThat(EXPECTED).containsOnlyKeys(24);
        assertThat(EXPECTED.get(24)).isEqualTo("恰一执行");
        List<String> scenarioMethods = Arrays.stream(TwoAdminAppIT.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("scenario"))
                .toList();
        assertThat(scenarioMethods).containsExactly("scenario24_定时发布多实例恰一触发");
        assertThat(scenarioMethods).noneMatch(name -> name.startsWith("scenario25"));
    }

    @Test
    void scenario24_定时发布多实例恰一触发() throws Exception {
        assertThat(EXPECTED.get(24)).isEqualTo("恰一执行");
        try (TwoAdminAppSupport env = new TwoAdminAppSupport(MYSQL, REDIS)) {
            long taskId = env.seedDueScheduled("s24_due");
            AtomicInteger ticks = new AtomicInteger();
            CyclicBarrier start = new CyclicBarrier(2);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            pool.submit(() -> tick(env.instanceA, start, done, ticks));
            pool.submit(() -> tick(env.instanceB, start, done, ticks));
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(ticks.get()).isEqualTo(2);
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM task_definition WHERE id = ?", String.class, taskId);
            Integer version = env.jdbc.queryForObject(
                    "SELECT version FROM task_definition WHERE id = ?", Integer.class, taskId);
            Integer snapshots = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?", Integer.class, taskId);
            assertThat(status).isEqualTo("PUBLISHED");
            assertThat(version).isEqualTo(1);
            assertThat(snapshots).isEqualTo(1);
        }
    }

    private static void tick(
            com.mkt.task.schedule.PublishScanScheduler scheduler,
            CyclicBarrier start,
            CountDownLatch done,
            AtomicInteger ticks) {
        try {
            start.await(10, TimeUnit.SECONDS);
            scheduler.tick();
            ticks.incrementAndGet();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            done.countDown();
        }
    }
}
