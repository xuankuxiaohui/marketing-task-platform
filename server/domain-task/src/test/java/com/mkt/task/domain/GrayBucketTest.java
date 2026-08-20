package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GrayBucketTest {

    @Test
    void standardVectors() {
        assertThat(GrayBucket.of(1L, 1L)).isEqualTo(81);
        assertThat(GrayBucket.of(1L, 2L)).isEqualTo(0);
        assertThat(GrayBucket.of(10001L, 88L)).isEqualTo(32);
    }
}
