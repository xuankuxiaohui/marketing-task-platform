package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxProducerTest {

    @Test
    void requireAdminOrPortal() {
        assertThat(OutboxProducer.require("admin")).isEqualTo(OutboxProducer.ADMIN);
        assertThat(OutboxProducer.require("portal")).isEqualTo(OutboxProducer.PORTAL);
        assertThat(OutboxProducer.ADMIN.id()).isEqualTo("admin");
        assertThatThrownBy(() -> OutboxProducer.require("worker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin|portal");
    }
}
