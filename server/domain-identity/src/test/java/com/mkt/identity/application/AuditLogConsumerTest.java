package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import com.mkt.infra.outbox.ConsumerDirection;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogConsumerTest {

    @Test
    void insertsAuditRowFromPayload() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        AuditLogConsumer consumer = new AuditLogConsumer(jdbc, new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
        assertThat(consumer.direction()).isEqualTo(ConsumerDirection.SYS_AUDIT_LOG);
        consumer.consume(new OutboxRecord(
                1L,
                "audit.log",
                "admin",
                "auth",
                "1",
                "{\"module\":\"auth\",\"action\":\"login\",\"operatorId\":1,\"operatorName\":\"alice\",\"ip\":\"1.1.1.1\",\"userAgent\":\"ua\",\"result\":\"SUCCESS\",\"requestSummary\":\"{}\"}",
                "PENDING",
                0,
                null,
                Instant.parse("2026-08-19T12:00:00Z")));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
