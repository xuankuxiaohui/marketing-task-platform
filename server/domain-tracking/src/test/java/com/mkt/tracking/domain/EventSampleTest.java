package com.mkt.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventSampleTest {

    @Test
    void onePercentKeepsIdsDivisibleByHundred() {
        assertThat(EventSample.include(100, 1)).isTrue();
        assertThat(EventSample.include(1, 1)).isFalse();
        assertThat(EventSample.include(99, 1)).isFalse();
        int kept = 0;
        for (long id = 1; id <= 10_000; id++) {
            if (EventSample.include(id, 1)) {
                kept++;
            }
        }
        assertThat(kept).isEqualTo(100);
    }

    @Test
    void hundredPercentKeepsAll() {
        assertThat(EventSample.include(1, 100)).isTrue();
        assertThat(EventSample.include(99, 100)).isTrue();
    }
}
