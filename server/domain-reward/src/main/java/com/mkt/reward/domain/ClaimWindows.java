package com.mkt.reward.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Daily claim window = UTC+8 natural day (appendix C / design §5.7). */
public final class ClaimWindows {

    public static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(8);

    private ClaimWindows() {}

    public static LocalDateTime dailyStartUtc(Clock clock) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_OFFSET);
        return businessDayStartUtc(today);
    }

    public static LocalDateTime businessDayStartUtc(LocalDate day) {
        return day.atStartOfDay().minusHours(8);
    }
}
