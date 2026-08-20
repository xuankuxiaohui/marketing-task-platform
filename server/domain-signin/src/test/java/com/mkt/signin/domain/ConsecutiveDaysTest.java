package com.mkt.signin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsecutiveDaysTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void emptyIsZero() {
        assertThat(ConsecutiveDays.compute(Set.of(), TODAY)).isZero();
        assertThat(ConsecutiveDays.compute(null, TODAY)).isZero();
    }

    @Test
    void includesTodayWhenSigned() {
        assertThat(ConsecutiveDays.compute(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY))
                .isEqualTo(3);
    }

    @Test
    void stillAliveIfYesterdaySignedAndTodayOpen() {
        assertThat(ConsecutiveDays.compute(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void missedYesterdayResetsUntilNextSign() {
        assertThat(ConsecutiveDays.compute(List.of(TODAY.minusDays(2)), TODAY)).isZero();
    }

    @Test
    void catchupRestoresChain() {
        assertThat(ConsecutiveDays.compute(
                        List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY))
                .isEqualTo(3);
    }

    @Test
    void crossesMonth() {
        LocalDate mar1 = LocalDate.of(2026, 3, 1);
        assertThat(ConsecutiveDays.compute(List.of(mar1, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 27)), mar1))
                .isEqualTo(3);
    }
}
