package com.mkt.contract.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventCodesTest {

    @Test
    void closedSetMatchesDesignD05() {
        assertThat(EventCodes.ALL)
                .containsExactlyInAnyOrder(
                        EventCodes.AUDIT_LOG,
                        EventCodes.AUTH_REGISTER_SUCCESS,
                        EventCodes.AUTH_LOGIN_SUCCESS,
                        EventCodes.TASK_INSTANCE_START,
                        EventCodes.TASK_STEP_COMPLETE,
                        EventCodes.TASK_INSTANCE_COMPLETE,
                        EventCodes.TASK_INSTANCE_ABANDON,
                        EventCodes.TASK_INSTANCE_EXPIRE,
                        EventCodes.RISK_HIT_RECORDED,
                        EventCodes.REWARD_GRANT_SUCCESS,
                        EventCodes.REWARD_GRANT_FAILED,
                        EventCodes.REWARD_FULFILL_ARRIVED,
                        EventCodes.REWARD_FULFILL_FAILED);
        assertThat(EventCodes.ALL).hasSize(13);
        assertThat(EventCodes.isInternal(EventCodes.AUDIT_LOG)).isTrue();
        assertThat(EventCodes.isAppendixD(EventCodes.AUDIT_LOG)).isFalse();
        assertThat(EventCodes.isAppendixD(EventCodes.TASK_INSTANCE_START)).isTrue();
        assertThat(EventCodes.isKnown("page.view")).isFalse();
        assertThatThrownBy(() -> EventCodes.requireKnown("order.paid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void domainEventRejectsUnknownCode() {
        assertThatThrownBy(() -> new DomainEvent("page.view", "user", "1", null))
                .isInstanceOf(IllegalArgumentException.class);
        DomainEvent event = new DomainEvent(EventCodes.AUDIT_LOG, "audit", "1", "payload");
        assertThat(event.eventCode()).isEqualTo(EventCodes.AUDIT_LOG);
        assertThat(EventCodes.isInternal(event.eventCode())).isTrue();
        assertThatThrownBy(() -> new DomainEvent(EventCodes.AUDIT_LOG, " ", "1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEvent(EventCodes.AUDIT_LOG, "audit", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
