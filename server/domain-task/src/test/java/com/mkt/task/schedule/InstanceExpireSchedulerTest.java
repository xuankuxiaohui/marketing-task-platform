package com.mkt.task.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.task.application.TaskInstanceAppService;
import org.junit.jupiter.api.Test;

class InstanceExpireSchedulerTest {

    @Test
    void busyLockSkipsExpire() {
        TaskInstanceAppService instances = mock(TaskInstanceAppService.class);
        PlatformLock locks = mock(PlatformLock.class);
        when(locks.tryLock("sched:instance-expire")).thenReturn(LockAcquire.BUSY);
        new InstanceExpireScheduler(instances, locks, java.time.Clock.systemUTC()).tick();
        verify(instances, never()).expireDue();
    }

    @Test
    void acquiredLockRunsExpireAndUnlocks() {
        TaskInstanceAppService instances = mock(TaskInstanceAppService.class);
        PlatformLock locks = new PlatformLock(new MemoryKeyValueStore());
        when(instances.expireDue()).thenReturn(3);
        new InstanceExpireScheduler(instances, locks, java.time.Clock.systemUTC()).tick();
        verify(instances).expireDue();
        org.assertj.core.api.Assertions.assertThat(locks.tryLock("sched:instance-expire"))
                .isEqualTo(LockAcquire.ACQUIRED);
    }
}
