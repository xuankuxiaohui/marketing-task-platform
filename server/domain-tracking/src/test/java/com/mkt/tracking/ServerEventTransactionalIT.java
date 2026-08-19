package com.mkt.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.event.EventCodes;
import com.mkt.tracking.it.TrackingITSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R28.3: business success lands server evt via Outbox; rollback leaves zero evt.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class ServerEventTransactionalIT {

    @Container
    static final MySQLContainer<?> MYSQL = TrackingITSupport.mysql();

    @Test
    void commitWritesEvtAndRollbackWritesNone() {
        TrackingITSupport env = TrackingITSupport.start(MYSQL, Instant.parse("2026-08-19T12:00:00Z"));

        env.tx.executeWithoutResult(status -> env.publisher.append(
                EventCodes.TASK_INSTANCE_START, "task_instance", "101", Map.of("userId", 101, "instanceId", 101)));
        env.drain.awaitDrain(Duration.ofSeconds(5));
        Integer afterCommit = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM evt_event_log WHERE source='SERVER' AND event_code=?",
                Integer.class,
                EventCodes.TASK_INSTANCE_START);
        assertThat(afterCommit).isEqualTo(1);

        env.tx.executeWithoutResult((TransactionStatus status) -> {
            env.publisher.append(
                    EventCodes.TASK_INSTANCE_COMPLETE, "task_instance", "202", Map.of("userId", 202, "instanceId", 202));
            assertThat(env.outbox.countPending("portal")).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(env.outbox.countPending("portal")).isZero();
        Integer afterRollback = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM evt_event_log WHERE event_code=?",
                Integer.class,
                EventCodes.TASK_INSTANCE_COMPLETE);
        assertThat(afterRollback).isZero();
        Integer outboxRows = env.jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox", Integer.class);
        assertThat(outboxRows).isZero();
    }
}
