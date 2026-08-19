package com.mkt.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.command.TrackBatchCommand;
import com.mkt.tracking.command.TrackEventCommand;
import com.mkt.tracking.command.TrackIdentity;
import com.mkt.tracking.it.TrackingITSupport;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackSettings;
import com.mkt.tracking.testsupport.ThrowingEventLogStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R28.1: track ingest outage does not change login/task/reward tables or block Outbox.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class TrackOutageNonBlockingIT {

    @Container
    static final MySQLContainer<?> MYSQL = TrackingITSupport.mysql();

    @Test
    void trackWriterDownLeavesBusinessTablesAndServerEventsIntact() {
        TrackingITSupport env = TrackingITSupport.start(MYSQL, Instant.parse("2026-08-19T12:00:00Z"));
        TrackBatchService broken = new TrackBatchService(
                new ThrowingEventLogStore(),
                env.metadata,
                new SlidingWindowRateLimiter(new MemoryKeyValueStore(), env.clock),
                new TrackSettings(),
                new TrackDropCounters(),
                env.clock);

        assertThatThrownBy(() -> broken.ingest(
                        new TrackBatchCommand(
                                List.of(new TrackEventCommand("page.view", Map.of("route", "/home"), "t")),
                                "WEB",
                                "1.0.0"),
                        new TrackIdentity(1L, "dev", "203.0.113.1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("track writer down");

        env.tx.executeWithoutResult(status -> env.publisher.append(
                EventCodes.TASK_INSTANCE_START, "task_instance", "1", Map.of("userId", 1L, "instanceId", 1L)));
        env.drain.awaitDrain(Duration.ofSeconds(5));

        Integer clientRows = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM evt_event_log WHERE source='CLIENT'", Integer.class);
        Integer serverRows = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM evt_event_log WHERE source='SERVER' AND event_code=?",
                Integer.class,
                EventCodes.TASK_INSTANCE_START);
        Integer instances = env.jdbc.queryForObject("SELECT COUNT(*) FROM task_instance", Integer.class);
        Integer grants = env.jdbc.queryForObject("SELECT COUNT(*) FROM rwd_grant_record", Integer.class);
        Integer points = env.jdbc.queryForObject("SELECT COUNT(*) FROM pnt_transaction", Integer.class);
        assertThat(clientRows).isZero();
        assertThat(serverRows).isEqualTo(1);
        assertThat(instances).isZero();
        assertThat(grants).isZero();
        assertThat(points).isZero();
    }
}
