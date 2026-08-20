package com.mkt.task.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Cycle key in UTC+8 (design §5.4 / R11.7). */
public final class CycleKeyResolver {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private CycleKeyResolver() {}

    public static String resolve(
            String cycleType, String cronExpr, Instant specialStart, Instant specialEnd, Instant now) {
        if (cycleType == null || CycleTypes.NONE.equals(cycleType)) {
            return CycleTypes.NONE;
        }
        ZonedDateTime zoned = now.atZone(ZONE);
        return switch (cycleType) {
            case CycleTypes.DAILY -> zoned.format(DAY);
            case CycleTypes.MONTHLY -> zoned.format(MONTH);
            case CycleTypes.CRON -> cronKey(cronExpr, zoned);
            case CycleTypes.SPECIAL -> specialKey(specialStart, specialEnd);
            default -> CycleTypes.NONE;
        };
    }

    public static Instant cycleEnd(
            String cycleType, String cronExpr, Instant specialStart, Instant specialEnd, Instant now) {
        if (cycleType == null || CycleTypes.NONE.equals(cycleType) || now == null) {
            return null;
        }
        ZonedDateTime zoned = now.atZone(ZONE);
        return switch (cycleType) {
            case CycleTypes.DAILY -> zoned.toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant().minusMillis(1);
            case CycleTypes.MONTHLY -> zoned.toLocalDate()
                    .withDayOfMonth(1)
                    .plusMonths(1)
                    .atStartOfDay(ZONE)
                    .toInstant()
                    .minusMillis(1);
            case CycleTypes.CRON -> {
                ZonedDateTime next = CronExprs.nextAfter(cronExpr, zoned);
                yield next == null ? null : next.toInstant().minusMillis(1);
            }
            case CycleTypes.SPECIAL -> specialEnd;
            default -> null;
        };
    }

    private static String cronKey(String cronExpr, ZonedDateTime now) {
        ZonedDateTime last = CronExprs.lastAtOrBefore(cronExpr, now);
        return last == null ? now.format(TS) : last.format(TS);
    }

    private static String specialKey(Instant specialStart, Instant specialEnd) {
        String start = specialStart == null ? "" : specialStart.atZone(ZONE).format(TS);
        String end = specialEnd == null ? "" : specialEnd.atZone(ZONE).format(TS);
        return start + "-" + end;
    }
}
