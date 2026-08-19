package com.mkt.tracking.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrackErrorCodesTest {

    @Test
    void overflowAndRateLimitMatchContract() {
        assertThat(TrackErrorCodes.BATCH_OVERFLOW.code()).isEqualTo("track.batch.overflow");
        assertThat(TrackErrorCodes.BATCH_OVERFLOW.httpStatus()).isEqualTo(400);
        assertThat(TrackErrorCodes.BATCH_RATE_LIMITED.code()).isEqualTo("track.batch.rate-limited");
        assertThat(TrackErrorCodes.BATCH_RATE_LIMITED.httpStatus()).isEqualTo(429);
    }
}
