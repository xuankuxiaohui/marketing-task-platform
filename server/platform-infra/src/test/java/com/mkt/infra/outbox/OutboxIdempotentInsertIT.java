package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** evt_event_log id reuses sys_outbox.id; PK conflict is success (design §6.4). Requires Docker. */
@Testcontainers
class OutboxIdempotentInsertIT {

    @Container
    static final MySQLContainer<?> MYSQL = OutboxITSupport.mysql();

    @Test
    void secondInsertWithSameIdIsSuccess() {
        JdbcTemplate jdbc = OutboxITSupport.migrate(MYSQL);
        jdbc.update(
                "INSERT INTO sys_outbox (event_code, producer, aggregate_type, aggregate_id, payload)"
                        + " VALUES (?,?,?,?,?)",
                OutboxRoutes.TASK_INSTANCE_START,
                "admin",
                "task_instance",
                "9",
                "{\"userId\":9}");
        Long outboxId = jdbc.queryForObject("SELECT id FROM sys_outbox WHERE aggregate_id = '9'", Long.class);
        OutboxRecord row = new OutboxRecord(
                outboxId,
                OutboxRoutes.TASK_INSTANCE_START,
                "admin",
                "task_instance",
                "9",
                "{\"userId\":9}",
                "PENDING",
                0,
                null,
                null);
        EvtEventLogWriter writer = new EvtEventLogWriter(jdbc, Clock.systemUTC());
        writer.consume(row);
        writer.consume(row);
        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM evt_event_log WHERE id = ?", Integer.class, outboxId);
        assertThat(events).isEqualTo(1);
    }
}
