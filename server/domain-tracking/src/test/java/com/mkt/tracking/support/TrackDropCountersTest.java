package com.mkt.tracking.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TrackDropCountersTest {

    @Test
    void addMethodsIncrementMicrometerWithReasonTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TrackDropCounters drops = new TrackDropCounters(registry);

        drops.addMalformed(2);
        drops.addUnregistered(3);
        drops.addDisabled(1);
        drops.addMalformed(0);
        drops.addUnregistered(-1);

        assertThat(drops.malformed()).isEqualTo(2);
        assertThat(drops.unregistered()).isEqualTo(3);
        assertThat(drops.disabled()).isEqualTo(1);
        assertThat(drops.totalDropped()).isEqualTo(6);

        assertThat(counter(registry, TrackDropCounters.REASON_MALFORMED).count()).isEqualTo(2.0);
        assertThat(counter(registry, TrackDropCounters.REASON_UNREGISTERED).count()).isEqualTo(3.0);
        assertThat(counter(registry, TrackDropCounters.REASON_DISABLED).count()).isEqualTo(1.0);
    }

    private static Counter counter(SimpleMeterRegistry registry, String reason) {
        Counter c = registry.find(TrackDropCounters.METRIC_NAME)
                .tag(TrackDropCounters.TAG_REASON, reason)
                .counter();
        assertThat(c).isNotNull();
        return c;
    }
}
