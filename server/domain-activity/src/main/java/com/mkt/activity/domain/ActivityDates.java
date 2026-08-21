package com.mkt.activity.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** UTC+8 natural days are the activity quota unit (R22). */
public final class ActivityDates {

    public static final ZoneOffset UTC8 = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private ActivityDates() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), UTC8);
    }

    public static String periodKey(Clock clock) {
        return today(clock).format(DAY);
    }

    public static String periodKey(LocalDate day) {
        return day.format(DAY);
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
}
