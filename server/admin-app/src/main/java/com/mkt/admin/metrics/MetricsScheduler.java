package com.mkt.admin.metrics;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** §6.7 metrics aggregate: 1min, tryLock(0), lock key sched:metrics-aggregate. */
public class MetricsScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricsScheduler.class);

    private final MetricsAggregateService aggregates;
    private final PlatformLock locks;
    private final Clock clock;

    public MetricsScheduler(MetricsAggregateService aggregates, PlatformLock locks, Clock clock) {
        this.aggregates = aggregates;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        String lockKey = LockKeys.sched("metrics-aggregate");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:metrics-aggregate:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int n = aggregates.run();
            log.info("sched:metrics-aggregate done, upserts={}", n);
        } catch (RuntimeException ex) {
            log.error("sched:metrics-aggregate failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
