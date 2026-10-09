package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BoundCycleEndTest {

    @Test
    void dailyKeyKeepsItsPeriodEvenWhenPersistedStartTimeRoundsIntoTheNextDay() {
        assertThat(CycleKeyResolver.cycleEndForKey(CycleTypes.DAILY, null, null, "20260819"))
                .isEqualTo(Instant.parse("2026-08-19T15:59:59.999Z"));
    }

    @Test
    void monthlyKeyDoesNotDriftToTheMonthInWhichOfflineOccurs() {
        assertThat(CycleKeyResolver.cycleEndForKey(CycleTypes.MONTHLY, null, null, "202608"))
                .isEqualTo(Instant.parse("2026-08-31T15:59:59.999Z"));
    }

    @Test
    void cronKeyAnchorsTheFrozenPeriodRatherThanTheNextTrigger() {
        assertThat(CycleKeyResolver.cycleEndForKey(CycleTypes.CRON, "0 * * * *", null, "20260819080000"))
                .isEqualTo(Instant.parse("2026-08-19T00:59:59.999Z"));
    }

    @Test
    void unboundedAndSpecialCyclesUseTheirOwnFrozenBoundary() {
        assertThat(CycleKeyResolver.cycleEndForKey(CycleTypes.NONE, null, null, "NONE")).isNull();
        Instant specialEnd = Instant.parse("2026-08-19T12:00:00Z");
        assertThat(CycleKeyResolver.cycleEndForKey(CycleTypes.SPECIAL, null, specialEnd, "special-key"))
                .isEqualTo(specialEnd);
    }
}
