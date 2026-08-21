package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MetricsPeriodsTest {

    @Test
    void dayWeekMonthKeys() {
        LocalDate day = LocalDate.of(2026, 8, 20);
        assertThat(MetricsPeriods.key(day, MetricsGrain.DAY)).isEqualTo("2026-08-20");
        assertThat(MetricsPeriods.key(day, MetricsGrain.WEEK)).isEqualTo("2026-W34");
        assertThat(MetricsPeriods.key(day, MetricsGrain.MONTH)).isEqualTo("2026-08");
    }
}
