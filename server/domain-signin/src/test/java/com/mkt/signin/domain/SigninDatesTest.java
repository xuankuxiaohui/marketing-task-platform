package com.mkt.signin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SigninDatesTest {

    @Test
    void utc8NaturalDayAroundMidnight() {
        Clock justBefore = Clock.fixed(Instant.parse("2026-08-19T16:00:00Z"), ZoneOffset.UTC);
        Clock justAfter = Clock.fixed(Instant.parse("2026-08-19T16:00:00.001Z"), ZoneOffset.UTC);
        assertThat(SigninDates.today(justBefore)).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(SigninDates.today(Clock.fixed(Instant.parse("2026-08-19T15:59:59Z"), ZoneOffset.UTC)))
                .isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(SigninDates.today(justAfter)).isEqualTo(LocalDate.of(2026, 8, 20));
    }
}
