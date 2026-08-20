package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.support.TaskErrorCodes;
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
            long good1 = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_g1")).id());
            long bad = env.tx.execute(
                    status -> env.defs.saveAggregate(PublishITSupport.disconnected("batch_bad")).id());
            long good2 = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("batch_g2")).id());
            List<BatchItemResponse> results = env.publishes.batchPublish(List.of(good1, bad, good2));
            assertThat(results).extracting(BatchItemResponse::success).containsExactly(true, false, true);
            assertThat(results.get(1).errorCode()).isEqualTo(TaskErrorCodes.PUBLISH_VALIDATE_FAILED.code());
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_definition WHERE id = ?", String.class, good1))
                    .isEqualTo("PUBLISHED");
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_definition WHERE id = ?", String.class, bad))
                    .isEqualTo("DRAFT");
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM task_definition WHERE id = ?", String.class, good2))
                    .isEqualTo("PUBLISHED");
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?",
                            Integer.class,
                            bad))
                    .isZero();
            assertThat(env.jdbc.queryForObject(
                            "SELECT version FROM task_definition WHERE id = ?", Integer.class, good1))
                    .isEqualTo(1);
            assertThat(env.jdbc.queryForObject(
                            "SELECT version FROM task_definition WHERE id = ?", Integer.class, bad))
                    .isZero();
        }
    }
}
