package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MetricsRatesTest {

    @Test
    void zeroDenominatorIsNull() {
        assertThat(MetricsRates.ratio(5, 0)).isNull();
        assertThat(MetricsRates.ratio(0, 0)).isNull();
    }

    @Test
    void ratioIsNumeratorOverDenominator() {
        assertThat(MetricsRates.ratio(1, 2)).isCloseTo(0.5, within(1e-9));
        assertThat(MetricsRates.ratio(0, 8)).isCloseTo(0.0, within(1e-9));
        assertThat(MetricsRates.ratio(3, 2)).isCloseTo(1.5, within(1e-9));
    }
}
