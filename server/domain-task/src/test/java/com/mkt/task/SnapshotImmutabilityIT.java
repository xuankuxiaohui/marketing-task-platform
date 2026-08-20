package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.task.command.PublishCommand;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R12.1: later publish must not mutate a bound snapshot. CI / Testcontainers. */
@Testcontainers
class SnapshotImmutabilityIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void laterPublishLeavesBoundSnapshotHashUnchanged() throws Exception {
        try (PublishITSupport env = new PublishITSupport(MYSQL)) {
            long taskId = env.tx.execute(status -> env.defs.saveAggregate(PublishITSupport.legal("snap_immut")).id());
            env.tx.executeWithoutResult(
                    status -> env.publishes.publish(taskId, new PublishCommand(null, null)));
            String v1 = env.jdbc.queryForObject(
                    "SELECT content FROM task_version_snapshot WHERE task_id = ? AND version = 1",
                    String.class,
                    taskId);
            Long snapshotId = env.jdbc.queryForObject(
                    "SELECT id FROM task_version_snapshot WHERE task_id = ? AND version = 1",
                    Long.class,
                    taskId);
            env.jdbc.update(
                    """
                    INSERT INTO task_instance
                    (task_id, task_code, version, snapshot_id, user_id, cycle_key, status, expire_at)
                    VALUES (?, 'snap_immut', 1, ?, 9, 'NONE', 'IN_PROGRESS', '2026-12-31 00:00:00.000')
                    """,
                    taskId,
                    snapshotId);
            env.tx.executeWithoutResult(status -> env.defs.saveAggregate(
                    PublishITSupport.renamed(PublishITSupport.legal("snap_immut"), taskId, "新名")));
            env.tx.executeWithoutResult(
                    status -> env.publishes.publish(taskId, new PublishCommand(true, null)));
            String v1After = env.jdbc.queryForObject(
                    "SELECT content FROM task_version_snapshot WHERE task_id = ? AND version = 1",
                    String.class,
                    taskId);
            assertThat(v1After).isEqualTo(v1);
            Long bound = env.jdbc.queryForObject(
                    "SELECT snapshot_id FROM task_instance WHERE task_id = ? AND user_id = 9",
                    Long.class,
                    taskId);
            assertThat(bound).isEqualTo(snapshotId);
            Integer instanceVersion = env.jdbc.queryForObject(
                    "SELECT version FROM task_instance WHERE task_id = ? AND user_id = 9",
                    Integer.class,
                    taskId);
            assertThat(instanceVersion).isEqualTo(1);
            assertThat(env.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM task_version_snapshot WHERE task_id = ?",
                            Integer.class,
                            taskId))
                    .isEqualTo(2);
        }
    }
}
