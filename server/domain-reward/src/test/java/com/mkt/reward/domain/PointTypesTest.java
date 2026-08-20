package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PointTypesTest {

    @Test
    void closedSet() {
        assertThat(PointTypes.closed("EARN")).isTrue();
        assertThat(PointTypes.closed("ADJUST")).isTrue();
        assertThat(PointTypes.closed("UNKNOWN")).isFalse();
        assertThat(PointTypes.closed(null)).isFalse();
    }
}
