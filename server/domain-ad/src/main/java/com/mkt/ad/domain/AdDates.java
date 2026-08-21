package com.mkt.ad.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** UTC+8 natural days are the freq unit (R30.4). Instants persist as UTC DATETIME(3). */
public final class AdDates {

    public static final ZoneOffset UTC8 = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private AdDates() {}

    public static String dayKey(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), UTC8).format(DAY);
    }

    public static Duration ttlToEndOfDay(Clock clock) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, UTC8);
        Instant end = today.plusDays(1).atStartOfDay(UTC8).toInstant();
        Duration ttl = Duration.between(now, end);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    public static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static boolean inWindow(Instant start, Instant end, Instant now) {
        if (now == null || start == null || end == null) {
            return false;
        }
        return !now.isBefore(start) && now.isBefore(end);
    }

    public static boolean overlap(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
