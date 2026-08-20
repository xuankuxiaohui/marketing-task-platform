package com.mkt.reward.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.reward.application.FulfillmentService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 10: fulfill retry, sending timeout, recon pool (design §6.7-10). */
public class FulfillRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FulfillRetryScheduler.class);

    private final FulfillmentService fulfillment;
    private final PlatformLock locks;
    private final Clock clock;

    public FulfillRetryScheduler(FulfillmentService fulfillment, PlatformLock locks, Clock clock) {
        this.fulfillment = fulfillment;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        String lockKey = LockKeys.sched("fulfill-retry");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:fulfill-retry:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int ran = fulfillment.tick();
            log.info("sched:fulfill-retry done, ran={}", ran);
        } catch (RuntimeException ex) {
            log.error("sched:fulfill-retry failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
