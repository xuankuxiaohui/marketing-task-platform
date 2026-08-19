package com.mkt.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PartitionNamesTest {

    @Test
    void nameAndBoundAreNextMonthExclusive() {
        YearMonth month = YearMonth.of(2026, 8);
        assertThat(PartitionNames.name(month)).isEqualTo("p202608");
        assertThat(PartitionNames.lessThanBound(month)).isEqualTo("2026-09-01 00:00:00");
        assertThat(PartitionNames.parseName("p202608")).isEqualTo(month);
        assertThat(PartitionNames.parseName("MAXVALUE")).isNull();
    }
}
