package com.mkt.signin.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.signin.application.SigninAdminAppService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class SigninPublishScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(SigninPublishScanScheduler.class);

    private final SigninAdminAppService admin;
    private final PlatformLock locks;
    private final Clock clock;

    public SigninPublishScanScheduler(SigninAdminAppService admin, PlatformLock locks, Clock clock) {
        this.admin = admin;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        String lockKey = LockKeys.sched("signin-publish-scan");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        TraceIds.put("sched:signin-publish-scan:" + clock.instant().toEpochMilli());
        try {
            int n = admin.publishDue(100);
            if (n > 0) {
                log.info("sched:signin-publish-scan done, published={}", n);
            }
        } catch (RuntimeException ex) {
            log.error("sched:signin-publish-scan failed", ex);
        } finally {
            locks.unlock(lockKey);
            TraceIds.clear();
        }
    }
}
