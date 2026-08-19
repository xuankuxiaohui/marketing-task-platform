package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Missing consumer / failed consume retries then DEAD; SELECT is producer-scoped. Requires Docker. */
@Testcontainers
class OutboxRelayRetryIT {

    @Container
    static final MySQLContainer<?> MYSQL = OutboxITSupport.mysql();

    @Test
    void missingRiskCntDoesNotDeleteAndFiveFailsMarkDead() {
        JdbcTemplate jdbc = OutboxITSupport.migrate(MYSQL);
        JdbcOutboxStore store = new JdbcOutboxStore(jdbc);
        jdbc.update(
                "INSERT INTO sys_outbox (event_code, producer, aggregate_type, aggregate_id, payload)"
                        + " VALUES (?,?,?,?,?)",
                OutboxRoutes.TASK_INSTANCE_COMPLETE,
                "admin",
                "task_instance",
                "1",
                "{}");
        jdbc.update(
                "INSERT INTO sys_outbox (event_code, producer, aggregate_type, aggregate_id, payload)"
                        + " VALUES (?,?,?,?,?)",
                OutboxRoutes.TASK_INSTANCE_START,
                "portal",
                "task_instance",
                "2",
                "{}");

        EventConsumer evt = new EvtEventLogWriter(jdbc, Clock.systemUTC());
        OutboxRelay adminRelay = new OutboxRelay(
                store, OutboxProducer.ADMIN, new PlatformLock(new MemoryKeyValueStore()), Clock.systemUTC(), List.of(evt));
        adminRelay.tick();

        Integer adminPending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox WHERE producer='admin' AND status='PENDING'", Integer.class);
        assertThat(adminPending).isEqualTo(1);
        Integer portalPending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox WHERE producer='portal' AND status='PENDING'", Integer.class);
        assertThat(portalPending).isEqualTo(1);

        jdbc.update(
                "INSERT INTO sys_outbox (event_code, producer, aggregate_type, aggregate_id, payload)"
                        + " VALUES (?,?,?,?,?)",
                OutboxRoutes.TASK_INSTANCE_START,
                "admin",
                "task_instance",
                "fail",
                "{}");
        EventConsumer boom = new EventConsumer() {
            @Override
            public ConsumerDirection direction() {
                return ConsumerDirection.EVT_EVENT_LOG;
            }

            @Override
            public void consume(OutboxRecord row) {
                if ("fail".equals(row.aggregateId())) {
                    throw new IllegalStateException("boom");
                }
            }
        };
        OutboxRelay failRelay = new OutboxRelay(
                store, OutboxProducer.ADMIN, new PlatformLock(new MemoryKeyValueStore()), Clock.systemUTC(), List.of(boom));
        for (int i = 0; i < 5; i++) {
            jdbc.update("UPDATE sys_outbox SET next_retry_at = NULL WHERE aggregate_id = 'fail'");
            failRelay.tick();
        }
        String status = jdbc.queryForObject(
                "SELECT status FROM sys_outbox WHERE aggregate_id = 'fail'", String.class);
        Integer retries = jdbc.queryForObject(
                "SELECT retry_count FROM sys_outbox WHERE aggregate_id = 'fail'", Integer.class);
        assertThat(status).isEqualTo("DEAD");
        assertThat(retries).isEqualTo(5);
    }
}
