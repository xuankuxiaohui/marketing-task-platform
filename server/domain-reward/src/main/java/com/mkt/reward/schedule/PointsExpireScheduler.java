package com.mkt.reward.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.reward.application.PointsAppService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 6: expire EARN rows (design §6.7-6 / §5.8). */
public class PointsExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(PointsExpireScheduler.class);

    private final PointsAppService points;
    private final PlatformLock locks;
    private final Clock clock;

    public PointsExpireScheduler(PointsAppService points, PlatformLock locks, Clock clock) {
        this.points = points;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void tick() {
        String lockKey = LockKeys.sched("points-expire");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:points-expire:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int ran = points.expireDue();
            log.info("sched:points-expire done, expired={}", ran);
        } catch (RuntimeException ex) {
            log.error("sched:points-expire failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
