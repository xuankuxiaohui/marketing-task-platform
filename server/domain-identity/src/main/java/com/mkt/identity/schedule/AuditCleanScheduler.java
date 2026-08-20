package com.mkt.identity.schedule;

import com.mkt.identity.config.ConfigService;
import com.mkt.identity.support.AuditConfigKeys;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 9: delete audit rows older than retention.audit-days, 5000 per batch. */
public class AuditCleanScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanScheduler.class);

    private final JdbcTemplate jdbc;
    private final PlatformLock locks;
    private final ConfigService configs;
    private final Clock clock;

    public AuditCleanScheduler(JdbcTemplate jdbc, PlatformLock locks, ConfigService configs, Clock clock) {
        this.jdbc = jdbc;
        this.locks = locks;
        this.configs = configs;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Shanghai")
    public void tick() {
        String lockKey = LockKeys.sched("audit-clean");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        try {
            run(clock.instant());
        } catch (RuntimeException ex) {
            log.error("sched:audit-clean failed", ex);
        } finally {
            locks.unlock(lockKey);
        }
    }

    public int run(Instant now) {
        int days = configs.getInt(AuditConfigKeys.RETENTION_DAYS, AuditConfigKeys.DEFAULT_RETENTION_DAYS);
        if (days < 1) {
            days = AuditConfigKeys.DEFAULT_RETENTION_DAYS;
        }
        Instant cutoff = now.minus(Duration.ofDays(days));
        int total = 0;
        while (true) {
            int deleted = jdbc.update(
                    "DELETE FROM sys_audit_log WHERE created_at < ? LIMIT ?",
                    Timestamp.from(cutoff),
                    AuditConfigKeys.CLEAN_BATCH);
            total += deleted;
            if (deleted < AuditConfigKeys.CLEAN_BATCH) {
                return total;
            }
        }
    }
}
