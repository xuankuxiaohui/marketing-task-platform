package com.mkt.kernel.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigurationTest {

    @Test
    void productionClockIsUtc() {
        Clock clock = new ClockConfiguration().clock();
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
