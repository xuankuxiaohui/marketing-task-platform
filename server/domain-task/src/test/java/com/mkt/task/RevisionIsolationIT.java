package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.PublishCommand;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R12.2: revision draft must not change the live snapshot. CI / Testcontainers. */
@Testcontainers
class RevisionIsolationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void savingRevisionDoesNotTouchSnapshot() throws Exception {
        try (PublishITSupport env = new PublishITSupport(MYSQL)) {
            long taskId = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("rev_iso")).id());
            env.tx.executeWithoutResult(
                    status -> env.publishes.publish(taskId, new PublishCommand(null, null)));
            String before = env.jdbc.queryForObject(
                    "SELECT content FROM task_version_snapshot WHERE task_id = ? AND version = 1",
                    String.class,
                    taskId);
            env.tx.executeWithoutResult(status -> env.defs.saveAggregate(
                    PublishITSupport.renamed(PublishITSupport.legal("rev_iso"), taskId, "修订草稿")));
            Integer pending = env.jdbc.queryForObject(
                    "SELECT pending_revision FROM task_definition WHERE id = ?", Integer.class, taskId);
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM task_definition WHERE id = ?", String.class, taskId);
            String after = env.jdbc.queryForObject(
                    "SELECT content FROM task_version_snapshot WHERE task_id = ? AND version = 1",
                    String.class,
                    taskId);
            assertThat(pending).isEqualTo(1);
            assertThat(status).isEqualTo("PUBLISHED");
            assertThat(after).isEqualTo(before);
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?",
                            Integer.class,
                            taskId))
                    .isEqualTo(1);
        }
    }
}
