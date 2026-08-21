package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class MetricsSchedulerTest {

    @Test
    void busyLockSkipsAggregate() {
        MetricsAggregateService aggregates = mock(MetricsAggregateService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:metrics-aggregate")).thenReturn(LockAcquire.BUSY);
        new MetricsScheduler(aggregates, locks, Clock.systemUTC()).tick();
        verify(aggregates, never()).run();
    }

    @Test
    void acquiredLockRunsAndUnlocks() {
        MetricsAggregateService aggregates = mock(MetricsAggregateService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(aggregates.run()).thenReturn(4);
        new MetricsScheduler(aggregates, locks, Clock.systemUTC()).tick();
        verify(aggregates).run();
        assertThat(locks.tryLock("sched:metrics-aggregate")).isEqualTo(LockAcquire.ACQUIRED);
    }

    @Test
    void failureStillUnlocks() {
        MetricsAggregateService aggregates = mock(MetricsAggregateService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(aggregates.run()).thenThrow(new IllegalStateException("boom"));
        new MetricsScheduler(aggregates, locks, Clock.systemUTC()).tick();
        assertThat(locks.tryLock("sched:metrics-aggregate")).isEqualTo(LockAcquire.ACQUIRED);
    }
}
