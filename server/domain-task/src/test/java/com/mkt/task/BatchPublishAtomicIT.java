package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R12.3: batch publish succeeds/fails per task; a failed task is unchanged. CI / Testcontainers. */
@Testcontainers
class BatchPublishAtomicIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void mixedBatchPublishesOnlyLegalTasks() throws Exception {
        try (PublishITSupport env = new PublishITSupport(MYSQL)) {
            List<Long> good = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                int n = i;
                good.add(env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_g" + n)).id()));
            }
            long empty = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.emptySteps("batch_empty")).id());
            long expr = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_expr")).id());
            env.jdbc.update("UPDATE task_definition SET filter_expr = 'eval(1)' WHERE id = ?", expr);
            long window = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_window")).id());
            env.jdbc.update(
                    "UPDATE task_definition SET start_time = '2026-08-20 00:00:00.000', end_time = '2026-08-19 00:00:00.000' WHERE id = ?",
                    window);
            long mutexA = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_mx_a")).id());
            long mutexB = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_mx_b")).id());
            env.jdbc.update("UPDATE task_definition SET mutex_group_id = 9, cycle_type = 'MONTHLY' WHERE id = ?", mutexA);
            env.jdbc.update("UPDATE task_definition SET mutex_group_id = 9, cycle_type = 'DAILY' WHERE id = ?", mutexB);
            long late = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_late")).id());
            env.jdbc.update(
                    "UPDATE task_definition SET end_time = '2026-08-21 00:00:00.000', schedule_publish_at = '2026-08-22 00:00:00.000' WHERE id = ?",
                    late);

            List<Long> ids = List.of(
                    good.get(0),
                    empty,
                    expr,
                    window,
                    mutexB,
                    late,
                    good.get(1),
                    good.get(2),
                    good.get(3),
                    good.get(4));
            List<BatchItemResponse> results = env.publishes.batchPublish(ids);
            assertThat(results).hasSize(10);
            assertThat(results.get(0).success()).isTrue();
            assertThat(results.subList(1, 6)).allMatch(item -> !item.success());
            assertThat(results.subList(1, 6))
                    .extracting(BatchItemResponse::errorCode)
                    .containsOnly(TaskErrorCodes.PUBLISH_VALIDATE_FAILED.code());
            assertThat(results.subList(6, 10)).allMatch(BatchItemResponse::success);
            for (Long id : List.of(empty, expr, window, mutexB, late)) {
                assertThat(env.jdbc.queryForObject("SELECT status FROM task_definition WHERE id = ?", String.class, id))
                        .isEqualTo("DRAFT");
                assertThat(env.jdbc.queryForObject("SELECT version FROM task_definition WHERE id = ?", Integer.class, id))
                        .isZero();
                assertThat(env.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?", Integer.class, id))
                        .isZero();
            }
            for (Long id : good) {
                assertThat(env.jdbc.queryForObject("SELECT status FROM task_definition WHERE id = ?", String.class, id))
                        .isEqualTo("PUBLISHED");
                assertThat(env.jdbc.queryForObject("SELECT version FROM task_definition WHERE id = ?", Integer.class, id))
                        .isEqualTo(1);
            }
        }
    }
}
