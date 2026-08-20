package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClaimWindowsTest {

    @Test
    void dailyStartIsUtcPlus8Midnight() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T16:30:00Z"), ZoneOffset.UTC);
        assertThat(ClaimWindows.dailyStartUtc(clock)).isEqualTo(LocalDateTime.parse("2026-08-19T16:00:00"));
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-08-19T15:59:59Z"), ZoneOffset.UTC);
        assertThat(ClaimWindows.dailyStartUtc(beforeMidnight)).isEqualTo(LocalDateTime.parse("2026-08-18T16:00:00"));
    }
}
