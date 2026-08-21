package com.mkt.task.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.task.application.TaskProgressReportStore;
import com.mkt.task.convert.TaskTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 7: delete progress-report rows older than 7 days, 5000 per batch (design §6.7-7). */
public class ProgressCleanScheduler {

    public static final int BATCH_SIZE = 5000;
    public static final Duration RETENTION = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(ProgressCleanScheduler.class);

    private final TaskProgressReportStore reports;
    private final PlatformLock locks;
    private final Clock clock;

    public ProgressCleanScheduler(TaskProgressReportStore reports, PlatformLock locks, Clock clock) {
        this.reports = reports;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * ?", zone = "Asia/Shanghai")
    public void tick() {
        String lockKey = LockKeys.sched("progress-clean");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:progress-clean:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int deleted = clean();
            log.info("sched:progress-clean done, deleted={}", deleted);
        } catch (RuntimeException ex) {
            log.error("sched:progress-clean failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }

    public int clean() {
        LocalDateTime cutoff = TaskTime.toUtc(clock.instant().minus(RETENTION));
        int total = 0;
        int batch;
        do {
            batch = reports.deleteBefore(cutoff, BATCH_SIZE);
            total += batch;
        } while (batch == BATCH_SIZE);
        return total;
    }
}
