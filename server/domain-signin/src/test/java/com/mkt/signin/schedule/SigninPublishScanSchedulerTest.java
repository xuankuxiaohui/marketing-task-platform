package com.mkt.signin.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.signin.application.SigninAdminAppService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class SigninPublishScanSchedulerTest {

    @Test
    void busyLockSkipsScan() {
        SigninAdminAppService admin = mock(SigninAdminAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:signin-publish-scan")).thenReturn(LockAcquire.BUSY);
        new SigninPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        verify(admin, never()).publishDue(100);
    }

    @Test
    void acquiredLockRunsScanAndUnlocks() {
        SigninAdminAppService admin = mock(SigninAdminAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(admin.publishDue(100)).thenReturn(2);
        new SigninPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        verify(admin).publishDue(100);
        assertThat(locks.tryLock("sched:signin-publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
    }

    @Test
    void scanFailureStillUnlocks() {
        SigninAdminAppService admin = mock(SigninAdminAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(admin.publishDue(100)).thenThrow(new IllegalStateException("boom"));
        new SigninPublishScanScheduler(admin, locks, Clock.systemUTC()).tick();
        assertThat(locks.tryLock("sched:signin-publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
    }
}
