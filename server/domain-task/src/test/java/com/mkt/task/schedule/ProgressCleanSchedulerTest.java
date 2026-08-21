package com.mkt.task.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskProgressReportEntity;
import com.mkt.task.testsupport.MemoryTaskProgressReportStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProgressCleanSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Test
    void busyLockSkipsClean() {
        MemoryTaskProgressReportStore reports = new MemoryTaskProgressReportStore();
        insert(reports, 1L, NOW.minus(ProgressCleanScheduler.RETENTION).minusSeconds(1));
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:progress-clean")).thenReturn(LockAcquire.BUSY);
        new ProgressCleanScheduler(reports, locks, Clock.fixed(NOW, ZoneOffset.UTC)).tick();
        assertThat(reports.rows).hasSize(1);
        verify(locks, never()).unlock("sched:progress-clean");
    }

    @Test
    void acquiredLockDeletesOlderThanSevenDaysAndUnlocks() {
        MemoryTaskProgressReportStore reports = new MemoryTaskProgressReportStore();
        insert(reports, 1L, NOW.minus(ProgressCleanScheduler.RETENTION).minusSeconds(1));
        insert(reports, 2L, NOW.minusSeconds(3600));
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        ProgressCleanScheduler scheduler =
                new ProgressCleanScheduler(reports, locks, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.tick();
        assertThat(reports.rows).hasSize(1);
        assertThat(reports.rows.values().iterator().next().getReportId()).isEqualTo("keep");
        assertThat(locks.tryLock("sched:progress-clean")).isEqualTo(LockAcquire.ACQUIRED);
    }

    @Test
    void cleanUsesBatchSizeConstant() {
        assertThat(ProgressCleanScheduler.BATCH_SIZE).isEqualTo(5000);
    }

    private static void insert(MemoryTaskProgressReportStore reports, long instanceId, Instant created) {
        TaskProgressReportEntity row = new TaskProgressReportEntity();
        row.setInstanceId(instanceId);
        row.setStepCode("p");
        row.setReportId(instanceId == 2L ? "keep" : "old");
        row.setValue(1);
        row.setCreatedAt(TaskTime.toUtc(created));
        reports.insert(row);
    }
}
