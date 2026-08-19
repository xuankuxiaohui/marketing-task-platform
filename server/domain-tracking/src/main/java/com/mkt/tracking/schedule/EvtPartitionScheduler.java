package com.mkt.tracking.schedule;

import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.tracking.domain.PartitionNames;
import com.mkt.tracking.support.TrackSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduler 8: pre-create 3 months; DROP partitions older than retention.event-days. */
public class EvtPartitionScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvtPartitionScheduler.class);
    private static final String TABLE = "evt_event_log";

    private final JdbcTemplate jdbc;
    private final PlatformLock locks;
    private final TrackSettings settings;
    private final Clock clock;

    public EvtPartitionScheduler(JdbcTemplate jdbc, PlatformLock locks, TrackSettings settings, Clock clock) {
        this.jdbc = jdbc;
        this.locks = locks;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void tick() {
        String lockKey = LockKeys.sched("evt-partition");
        LockAcquire acquired = locks.tryLock(lockKey);
        if (acquired != LockAcquire.ACQUIRED) {
            return;
        }
        try {
            run(clock.instant());
        } catch (RuntimeException ex) {
            log.error("sched:evt-partition failed", ex);
        } finally {
            locks.unlock(lockKey);
        }
    }

    public void run(Instant now) {
        YearMonth current = YearMonth.from(now.atZone(ZoneOffset.UTC));
        Set<String> existing = existingNames();
        for (int i = 0; i <= 3; i++) {
            YearMonth month = current.plusMonths(i);
            String name = PartitionNames.name(month);
            if (!existing.contains(name)) {
                addPartition(name, PartitionNames.lessThanBound(month));
                existing.add(name);
            }
        }
        Instant cutoff = now.minusSeconds(settings.retentionEventDays() * 86400L);
        YearMonth keepFrom = YearMonth.from(cutoff.atZone(ZoneOffset.UTC));
        List<String> drop = new ArrayList<>();
        for (String name : existing) {
            YearMonth month = PartitionNames.parseName(name);
            if (month != null && month.isBefore(keepFrom) && !month.equals(current)) {
                drop.add(name);
            }
        }
        for (String name : drop) {
            if (existing.size() <= 1) {
                break;
            }
            dropPartition(name);
            existing.remove(name);
        }
    }

    private Set<String> existingNames() {
        List<String> names = jdbc.query(
                "SELECT PARTITION_NAME FROM information_schema.PARTITIONS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL",
                (rs, rowNum) -> rs.getString(1),
                TABLE);
        return new HashSet<>(names);
    }

    private void addPartition(String name, String bound) {
        requireSafeName(name);
        requireSafeBound(bound);
        jdbc.execute(
                "ALTER TABLE evt_event_log ADD PARTITION (PARTITION " + name + " VALUES LESS THAN ('" + bound + "'))");
        log.info("evt partition added {}", name);
    }

    private void dropPartition(String name) {
        requireSafeName(name);
        jdbc.execute("ALTER TABLE evt_event_log DROP PARTITION " + name);
        log.info("evt partition dropped {}", name);
    }

    private static void requireSafeName(String name) {
        if (name == null || !name.matches("p[0-9]{6}")) {
            throw new IllegalArgumentException("invalid partition name");
        }
    }

    private static void requireSafeBound(String bound) {
        if (bound == null || !bound.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} 00:00:00")) {
            throw new IllegalArgumentException("invalid partition bound");
        }
    }
}
