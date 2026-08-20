package com.mkt.task.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.task.application.TaskInstanceAppService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 2: flip IN_PROGRESS instances with expire_at &lt;= now (design §6.7-2). */
public class InstanceExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstanceExpireScheduler.class);

    private final TaskInstanceAppService instances;
    private final PlatformLock locks;
    private final Clock clock;

    public InstanceExpireScheduler(TaskInstanceAppService instances, PlatformLock locks, Clock clock) {
        this.instances = instances;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        String lockKey = LockKeys.sched("instance-expire");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:instance-expire:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int expired = instances.expireDue();
            log.info("sched:instance-expire done, expired={}", expired);
        } catch (RuntimeException ex) {
            log.error("sched:instance-expire failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
