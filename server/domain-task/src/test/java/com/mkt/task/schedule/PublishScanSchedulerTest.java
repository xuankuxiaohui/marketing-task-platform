package com.mkt.task.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.task.application.TaskPublishAppService;
import org.junit.jupiter.api.Test;

class PublishScanSchedulerTest {

    @Test
    void busyLockSkipsScan() {
        TaskPublishAppService publishes = mock(TaskPublishAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:publish-scan")).thenReturn(LockAcquire.BUSY);
        new PublishScanScheduler(publishes, locks, java.time.Clock.systemUTC()).tick();
        verify(publishes, never()).scanDue();
    }

    @Test
    void acquiredLockRunsScanAndUnlocks() {
        TaskPublishAppService publishes = mock(TaskPublishAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(publishes.scanDue()).thenReturn(2);
        new PublishScanScheduler(publishes, locks, java.time.Clock.systemUTC()).tick();
        verify(publishes).scanDue();
        org.assertj.core.api.Assertions.assertThat(locks.tryLock("sched:publish-scan"))
                .isEqualTo(LockAcquire.ACQUIRED);
    }
}
