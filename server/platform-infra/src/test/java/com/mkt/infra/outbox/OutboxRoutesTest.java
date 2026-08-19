package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutboxRoutesTest {

    @Test
    void closedTableMatchesDesign64() {
        assertThat(OutboxRoutes.all()).hasSize(13);
        assertThat(OutboxRoutes.directions(OutboxRoutes.AUDIT_LOG)).containsExactly(ConsumerDirection.SYS_AUDIT_LOG);
        assertThat(OutboxRoutes.directions(OutboxRoutes.TASK_INSTANCE_COMPLETE))
                .containsExactly(ConsumerDirection.EVT_EVENT_LOG, ConsumerDirection.RISK_CNT);
        assertThat(OutboxRoutes.directions(OutboxRoutes.REWARD_GRANT_SUCCESS))
                .contains(ConsumerDirection.RISK_CNT);
        assertThat(OutboxRoutes.directions("unknown.event")).isEmpty();
        assertThat(OutboxRoutes.declared(OutboxRoutes.AUTH_LOGIN_SUCCESS)).isTrue();
    }
}
