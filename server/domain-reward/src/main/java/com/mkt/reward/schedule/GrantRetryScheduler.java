package com.mkt.reward.schedule;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantStepResumer;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 3: retry RETRY_PENDING grants (design §6.7-3). */
public class GrantRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(GrantRetryScheduler.class);

    private final GrantAppService grants;
    private final GrantStepResumer resumer;
    private final PlatformLock locks;
    private final Clock clock;

    public GrantRetryScheduler(GrantAppService grants, GrantStepResumer resumer, PlatformLock locks, Clock clock) {
        this.grants = grants;
        this.resumer = resumer;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        String lockKey = LockKeys.sched("grant-retry");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        String trace = "sched:grant-retry:" + clock.instant().toEpochMilli();
        TraceIds.put(trace);
        try {
            int ran = 0;
            for (GrantRecordEntity row : grants.listDueRetry()) {
                try {
                    GrantSource source = GrantSource.valueOf(row.getGrantSource());
                    boolean simulated = row.getSimulated() != null && row.getSimulated() == 1;
                    GrantResult result = grants.grant(
                            row.getPrizeId(),
                            row.getUserId(),
                            source,
                            row.getSourceId(),
                            new GrantContext(null, List.of(), null, simulated, null));
                    if ((result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON)
                            && resumer != null) {
                        resumer.resume(source, row.getSourceId());
                    }
                    ran++;
                } catch (RuntimeException ex) {
                    log.warn("sched:grant-retry item failed id={}", row.getId(), ex);
                    ran++;
                }
            }
            log.info("sched:grant-retry done, retried={}", ran);
        } catch (RuntimeException ex) {
            log.error("sched:grant-retry failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
