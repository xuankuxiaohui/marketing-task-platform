package com.mkt.reward.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.reward.application.ClaimAppService;
import org.junit.jupiter.api.Test;

class ClaimTimeoutSchedulerTest {

    @Test
    void busyLockSkips() {
        ClaimAppService claims = mock(ClaimAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:claim-timeout")).thenReturn(LockAcquire.BUSY);
        new ClaimTimeoutScheduler(claims, locks, java.time.Clock.systemUTC()).tick();
        verify(claims, never()).rollbackClaimingTimeout();
    }

    @Test
    void acquiredRunsAndUnlocks() {
        ClaimAppService claims = mock(ClaimAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        new ClaimTimeoutScheduler(claims, locks, java.time.Clock.systemUTC()).tick();
        verify(claims).rollbackClaimingTimeout();
    }
}
