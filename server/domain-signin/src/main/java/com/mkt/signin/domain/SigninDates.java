package com.mkt.signin.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** UTC+8 natural days are the signin calendar unit (R21.2). */
public final class SigninDates {

    public static final ZoneOffset UTC8 = ZoneOffset.ofHours(8);

    private SigninDates() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), UTC8);
    }

    public static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static Instant startOfDayUtc8(LocalDate day) {
        return day.atStartOfDay(UTC8).toInstant();
    }

    public static LocalDateTime startOfDayUtc(LocalDate utc8Day) {
        return LocalDateTime.ofInstant(startOfDayUtc8(utc8Day), ZoneOffset.UTC);
    }
}
