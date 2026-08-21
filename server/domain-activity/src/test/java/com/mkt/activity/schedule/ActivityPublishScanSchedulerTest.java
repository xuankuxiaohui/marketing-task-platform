package com.mkt.activity.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ActivityPublishScanSchedulerTest {

    @Test
    void busyLockSkipsScan() {
        ActivityAdminAppService admin = mock(ActivityAdminAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:activity-publish-scan")).thenReturn(LockAcquire.BUSY);
        new ActivityPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        verify(admin, never()).publishDue(100);
    }

    @Test
    void acquiredLockRunsScanAndUnlocks() {
        ActivityAdminAppService admin = mock(ActivityAdminAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(admin.publishDue(100)).thenReturn(2);
        when(admin.offlineDue(100)).thenReturn(1);
        new ActivityPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        verify(admin).publishDue(100);
        verify(admin).offlineDue(100);
        assertThat(locks.tryLock("sched:activity-publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
    }

    @Test
    void scanFailureStillUnlocks() {
        ActivityAdminAppService admin = mock(ActivityAdminAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(admin.publishDue(100)).thenThrow(new IllegalStateException("boom"));
        new ActivityPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        assertThat(locks.tryLock("sched:activity-publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
    }
}
