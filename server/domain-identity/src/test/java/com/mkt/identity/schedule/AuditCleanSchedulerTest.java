package com.mkt.identity.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.support.AuditConfigKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditCleanSchedulerTest {

    @Test
    void deletesInBatchesOf5000UntilShortPage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), eq(AuditConfigKeys.CLEAN_BATCH)))
                .thenReturn(5000, 5000, 12);
        AuditCleanScheduler scheduler = new AuditCleanScheduler(
                jdbc, new PlatformLock(new MemoryKeyValueStore()), (key, def) -> 180, java.time.Clock.systemUTC());

        int deleted = scheduler.run(Instant.parse("2026-08-19T12:00:00Z"));

        assertThat(deleted).isEqualTo(10012);
        ArgumentCaptor<Object> cutoff = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(3)).update(anyString(), cutoff.capture(), eq(5000));
        Timestamp ts = (Timestamp) cutoff.getAllValues().get(0);
        assertThat(ts.toInstant()).isEqualTo(Instant.parse("2026-02-20T12:00:00Z"));
    }

    @Test
    void busyLockSkipsTick() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        assertThat(locks.tryLock("sched:audit-clean")).isEqualTo(com.mkt.infra.lock.LockAcquire.ACQUIRED);
        AuditCleanScheduler scheduler =
                new AuditCleanScheduler(jdbc, locks, (key, def) -> 180, java.time.Clock.systemUTC());
        scheduler.tick();
        verify(jdbc, never()).update(anyString(), any(), any());
    }
}
