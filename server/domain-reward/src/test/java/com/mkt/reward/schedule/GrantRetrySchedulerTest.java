package com.mkt.reward.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantStepResumer;
import com.mkt.reward.entity.GrantRecordEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantRetrySchedulerTest {

    @Test
    void busyLockSkipsRetry() {
        GrantAppService grants = mock(GrantAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:grant-retry")).thenReturn(LockAcquire.BUSY);
        new GrantRetryScheduler(grants, null, locks, java.time.Clock.systemUTC()).tick();
        verify(grants, never()).listDueRetry();
    }

    @Test
    void acquiredLockListsDueAndUnlocks() {
        GrantAppService grants = mock(GrantAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(grants.listDueRetry()).thenReturn(List.of());
        new GrantRetryScheduler(grants, null, locks, java.time.Clock.systemUTC()).tick();
        verify(grants).listDueRetry();
        org.assertj.core.api.Assertions.assertThat(locks.tryLock("sched:grant-retry"))
                .isEqualTo(LockAcquire.ACQUIRED);
    }

    @Test
    void successfulRetryResumesTaskStep() {
        GrantAppService grants = mock(GrantAppService.class);
        GrantStepResumer resumer = mock(GrantStepResumer.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        GrantRecordEntity row = new GrantRecordEntity();
        row.setId(4L);
        row.setPrizeId(8L);
        row.setUserId(9L);
        row.setGrantSource("TASK_STEP");
        row.setSourceId("12");
        row.setSimulated(0);
        when(grants.listDueRetry()).thenReturn(List.of(row));
        when(grants.grant(eq(8L), eq(9L), eq(GrantSource.TASK_STEP), eq("12"), any()))
                .thenReturn(new GrantResult(4L, GrantStatus.GRANTED, FulfillmentStatus.ARRIVED, 8L, false));
        new GrantRetryScheduler(grants, resumer, locks, java.time.Clock.systemUTC()).tick();
        verify(resumer).resume(GrantSource.TASK_STEP, "12");
    }
}
