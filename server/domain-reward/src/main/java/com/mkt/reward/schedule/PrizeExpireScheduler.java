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

/** Scheduler 5: WON / RETRY_PENDING expire (design §6.7-5). */
public class PrizeExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrizeExpireScheduler.class);

    private final ClaimAppService claims;
    private final PlatformLock locks;
    private final Clock clock;

    public PrizeExpireScheduler(ClaimAppService claims, PlatformLock locks, Clock clock) {
        this.claims = claims;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        String lockKey = LockKeys.sched("prize-expire");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:prize-expire:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int ran = claims.expireDue();
            log.info("sched:prize-expire done, expired={}", ran);
        } catch (RuntimeException ex) {
            log.error("sched:prize-expire failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
