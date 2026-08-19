package com.mkt.tracking.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.tracking.support.TrackSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EvtPartitionSchedulerTest {

    @Test
    void preCreatesFutureMonthsAndDropsExpired() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("evt_event_log")))
                .thenReturn(new ArrayList<>(List.of("p202501", "p202608")));
        TrackSettings settings = new TrackSettings();
        settings.setRetentionEventDays(90);
        EvtPartitionScheduler scheduler =
                new EvtPartitionScheduler(jdbc, new PlatformLock(new MemoryKeyValueStore()), settings, java.time.Clock.systemUTC());

        scheduler.run(Instant.parse("2026-08-19T12:00:00Z"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeast(1)).execute(sql.capture());
        List<String> statements = sql.getAllValues();
        assertThat(statements).anyMatch(s -> s.contains("ADD PARTITION") && s.contains("p202609"));
        assertThat(statements).anyMatch(s -> s.contains("ADD PARTITION") && s.contains("p202610"));
        assertThat(statements).anyMatch(s -> s.contains("ADD PARTITION") && s.contains("p202611"));
        assertThat(statements).anyMatch(s -> s.contains("DROP PARTITION p202501"));
        assertThat(statements).noneMatch(s -> s.contains("DROP PARTITION p202608"));
        assertThat(statements).noneMatch(s -> s.contains("sys_audit_log"));
    }
}
