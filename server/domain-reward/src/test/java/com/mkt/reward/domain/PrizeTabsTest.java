package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrizeTabsTest {

    @Test
    void pendingIncludesWonSendingAndFailedFulfillment() {
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.WON, GrantRecordStatuses.FULFILL_NONE)).isTrue();
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.GRANTED, GrantRecordStatuses.FULFILL_SENDING))
                .isTrue();
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.GRANTED, GrantRecordStatuses.FULFILL_FAILED))
                .isTrue();
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.GRANTED, GrantRecordStatuses.FULFILL_ARRIVED))
                .isFalse();
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.EXPIRED, GrantRecordStatuses.FULFILL_NONE)).isFalse();
        assertThat(PrizeTabs.pendingVisible(GrantRecordStatuses.PERMANENT_FAILED, GrantRecordStatuses.FULFILL_NONE))
                .isFalse();
    }
}
