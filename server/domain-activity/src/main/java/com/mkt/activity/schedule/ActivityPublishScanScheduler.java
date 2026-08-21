package com.mkt.activity.schedule;

import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ActivityPublishScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivityPublishScanScheduler.class);

    private final ActivityAdminAppService admin;
    private final PlatformLock locks;
    private final Clock clock;

    public ActivityPublishScanScheduler(ActivityAdminAppService admin, PlatformLock locks, Clock clock) {
        this.admin = admin;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        String lockKey = LockKeys.sched("activity-publish-scan");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        TraceIds.put("sched:activity-publish-scan:" + clock.instant().toEpochMilli());
        try {
            int published = admin.publishDue(100);
            int offlined = admin.offlineDue(100);
            if (published > 0 || offlined > 0) {
                log.info("sched:activity-publish-scan done, published={} offlined={}", published, offlined);
            }
        } catch (RuntimeException ex) {
            log.error("sched:activity-publish-scan failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
