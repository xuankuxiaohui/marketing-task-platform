package com.mkt.infra.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class PlatformLockTest {

    @Test
    void tryLockZeroWaitIsExclusiveThenDegradesWhenDown() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        PlatformLock locks = new PlatformLock(store);
        assertThat(locks.tryLock("sched:publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
        assertThat(locks.tryLock("sched:publish-scan")).isEqualTo(LockAcquire.BUSY);
        locks.unlock("sched:publish-scan");
        assertThat(locks.tryClaimLock(3L)).isEqualTo(LockAcquire.ACQUIRED);
        store.setAvailable(false);
        assertThat(locks.tryClaimLock(4L)).isEqualTo(LockAcquire.DEGRADED);
    }
}
