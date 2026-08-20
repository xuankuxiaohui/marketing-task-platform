package com.mkt.task.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * expire_at before INSERT (R14.10 / design §5.5).
 * {@code base = min(snapshot.endTime, definition.offlineAt, cycleEnd)}; missing = +∞.
 * If still +∞, {@code base = now}. Then {@code expireAt = base + N days}.
 */
public final class ExpireAtCalculator {

    private ExpireAtCalculator() {}

    public static Instant compute(
            Instant snapshotEndTime,
            Instant offlineAt,
            Instant cycleEnd,
            Instant now,
            int expireAfterWindowDays) {
        Instant base = min(snapshotEndTime, offlineAt);
        base = min(base, cycleEnd);
        if (base == null) {
            base = now;
        }
        int days = expireAfterWindowDays < 1 ? 7 : expireAfterWindowDays;
        return base.plus(days, ChronoUnit.DAYS);
    }

    private static Instant min(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
