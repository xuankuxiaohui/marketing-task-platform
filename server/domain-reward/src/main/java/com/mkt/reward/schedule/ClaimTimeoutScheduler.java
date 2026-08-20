package com.mkt.reward.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.reward.application.ClaimAppService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 4: CLAIMING timeout rollback to WON (design §6.7-4). */
public class ClaimTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClaimTimeoutScheduler.class);

    private final ClaimAppService claims;
    private final PlatformLock locks;
    private final Clock clock;

    public ClaimTimeoutScheduler(ClaimAppService claims, PlatformLock locks, Clock clock) {
        this.claims = claims;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 10_000)
    public void tick() {
        String lockKey = LockKeys.sched("claim-timeout");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:claim-timeout:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int ran = claims.rollbackClaimingTimeout();
            log.info("sched:claim-timeout done, rolled={}", ran);
        } catch (RuntimeException ex) {
            log.error("sched:claim-timeout failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
