package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExpireAtCalculatorTest {

    @Test
    void minOfWindowOfflineAndCycleThenPlusDays() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        Instant end = Instant.parse("2026-08-20T00:00:00Z");
        Instant offline = Instant.parse("2026-08-21T00:00:00Z");
        Instant cycleEnd = Instant.parse("2026-08-19T12:00:00Z");
        Instant expireAt = ExpireAtCalculator.compute(end, offline, cycleEnd, now, 7);
        assertThat(expireAt).isEqualTo(Instant.parse("2026-08-26T12:00:00Z"));
    }

    @Test
    void unboundedOneShotUsesNowAsBase() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        Instant expireAt = ExpireAtCalculator.compute(null, null, null, now, 7);
        assertThat(expireAt).isEqualTo(Instant.parse("2026-08-26T00:00:00Z"));
    }

    @Test
    void nullWindowUsesOfflineWhenPresent() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        Instant offline = Instant.parse("2026-08-20T00:00:00Z");
        Instant expireAt = ExpireAtCalculator.compute(null, offline, null, now, 7);
        assertThat(expireAt).isEqualTo(Instant.parse("2026-08-27T00:00:00Z"));
    }

    @Test
    void nullOfflineAndWindowUsesCycleEnd() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        Instant cycleEnd = Instant.parse("2026-08-19T15:59:59.999Z");
        Instant expireAt = ExpireAtCalculator.compute(null, null, cycleEnd, now, 7);
        assertThat(expireAt).isEqualTo(Instant.parse("2026-08-26T15:59:59.999Z"));
    }
}
