package com.mkt.kernel.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    void setInstantMovesTheClock() {
        Instant start = Instant.parse("2026-08-16T04:00:00Z");
        Instant later = Instant.parse("2026-08-16T05:00:00Z");
        MutableClock clock = new MutableClock(start);

        assertThat(clock.instant()).isEqualTo(start);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);

        clock.setInstant(later);

        assertThat(clock.instant()).isEqualTo(later);
        assertThat(clock.instant()).isNotEqualTo(start);
    }

    @Test
    void withZoneSharesTheSameInstant() {
        Instant start = Instant.parse("2026-08-16T04:00:00Z");
        Instant later = Instant.parse("2026-08-17T00:00:00Z");
        MutableClock utc = new MutableClock(start);
        MutableClock plusEight = (MutableClock) utc.withZone(ZoneOffset.ofHours(8));

        utc.setInstant(later);

        assertThat(plusEight.instant()).isEqualTo(later);
        assertThat(plusEight.getZone()).isEqualTo(ZoneOffset.ofHours(8));
    }
}
