package com.mkt.identity.convert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IdentityTimeTest {

    @Test
    void roundTripUtcAndNull() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        LocalDateTime utc = IdentityTime.toUtc(now);
        assertThat(IdentityTime.toInstant(utc)).isEqualTo(now);
        assertThat(IdentityTime.toUtc(null)).isNull();
        assertThat(IdentityTime.toInstant(null)).isNull();
    }
}
