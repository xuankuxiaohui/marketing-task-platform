package com.mkt.reward.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.reward.application.FulfillmentService;
import org.junit.jupiter.api.Test;

class FulfillRetrySchedulerTest {

    @Test
    void busyLockSkips() {
        FulfillmentService fulfillment = mock(FulfillmentService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:fulfill-retry")).thenReturn(LockAcquire.BUSY);
        new FulfillRetryScheduler(fulfillment, locks, java.time.Clock.systemUTC()).tick();
        verify(fulfillment, never()).tick();
    }
}
