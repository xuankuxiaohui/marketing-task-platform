package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class EvtEventLogWriterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EvtEventLogWriter writer =
            new EvtEventLogWriter(jdbc, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void writesServerRowAndTreatsDuplicateAsSuccess() {
        assertThat(writer.direction()).isEqualTo(ConsumerDirection.EVT_EVENT_LOG);
        OutboxRecord row = new OutboxRecord(
                7L,
                OutboxRoutes.TASK_INSTANCE_START,
                "admin",
                "task_instance",
                "1",
                "{\"userId\":3,\"simulated\":true}",
                "PENDING",
                0,
                null,
                Instant.now());
        writer.consume(row);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any());

        doThrow(new DuplicateKeyException("dup"))
                .when(jdbc)
                .update(anyString(), any(), any(), any(), any(), any(), any(), any());
        writer.consume(row);

        writer.consume(new OutboxRecord(
                8L, OutboxRoutes.AUDIT_LOG, "admin", "audit", "1", "{}", "PENDING", 0, null, Instant.now()));
        verify(jdbc, times(2)).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void parseNonObjectPayload() {
        writer.consume(new OutboxRecord(
                9L, OutboxRoutes.TASK_STEP_COMPLETE, "admin", "t", "1", "not-json", "PENDING", 0, null, Instant.now()));
        writer.consume(new OutboxRecord(
                10L, OutboxRoutes.TASK_STEP_COMPLETE, "admin", "t", "1", "null", "PENDING", 0, null, Instant.now()));
        verify(jdbc, times(2)).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }
}
