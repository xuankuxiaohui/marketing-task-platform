package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxBackoffTest {

    @Test
    void stepsThenDeadAtFive() {
        assertThat(OutboxBackoff.delayAfterFailure(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(OutboxBackoff.delayAfterFailure(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(OutboxBackoff.delayAfterFailure(3)).isEqualTo(Duration.ofMinutes(2));
        assertThat(OutboxBackoff.delayAfterFailure(4)).isEqualTo(Duration.ofMinutes(10));
        assertThat(OutboxBackoff.delayAfterFailure(5)).isEqualTo(Duration.ofMinutes(30));
        assertThat(OutboxBackoff.dead(4)).isFalse();
        assertThat(OutboxBackoff.dead(5)).isTrue();
    }
}
