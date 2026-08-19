package com.mkt.infra.lock;

/** tryLock(0) outcome (design §6.8). DEGRADED = Redis down, caller may CAS. */
public enum LockAcquire {
    ACQUIRED,
    BUSY,
    DEGRADED
}
