package com.mkt.task.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.task.application.TaskPublishAppService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 1: timed publish scan every 30s (design §6.7-1). */
public class PublishScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishScanScheduler.class);

    private final TaskPublishAppService publishes;
    private final PlatformLock locks;
    private final Clock clock;

    public PublishScanScheduler(TaskPublishAppService publishes, PlatformLock locks, Clock clock) {
        this.publishes = publishes;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        String lockKey = LockKeys.sched("publish-scan");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:publish-scan:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int published = publishes.scanDue();
            log.info("sched:publish-scan done, published={}", published);
        } catch (RuntimeException ex) {
            log.error("sched:publish-scan failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
