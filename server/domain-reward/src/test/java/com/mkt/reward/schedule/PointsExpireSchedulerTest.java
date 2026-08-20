package com.mkt.reward.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.reward.application.PointsAppService;
import org.junit.jupiter.api.Test;

class PointsExpireSchedulerTest {

    @Test
    void busyLockSkips() {
        PointsAppService points = mock(PointsAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:points-expire")).thenReturn(LockAcquire.BUSY);
        new PointsExpireScheduler(points, locks, java.time.Clock.systemUTC()).tick();
        verify(points, never()).expireDue();
    }

    @Test
    void acquiredRunsExpire() {
        PointsAppService points = mock(PointsAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:points-expire")).thenReturn(LockAcquire.ACQUIRED);
        new PointsExpireScheduler(points, locks, java.time.Clock.systemUTC()).tick();
        verify(points).expireDue();
        verify(locks).unlock("sched:points-expire");
    }
}
