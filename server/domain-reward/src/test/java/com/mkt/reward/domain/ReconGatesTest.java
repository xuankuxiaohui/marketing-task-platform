package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconGatesTest {

    @Test
    void sendingAndTimeoutNeedReviewRegardlessOfPolicy() {
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY, GrantRecordStatuses.FULFILL_SENDING, null))
                .isTrue();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.TIMEOUT))
                .isTrue();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY, GrantRecordStatuses.FULFILL_FAILED, null))
                .isTrue();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY, GrantRecordStatuses.FULFILL_FAILED, "WEIRD"))
                .isTrue();
    }

    @Test
    void channelRejectDoesNotNeedReview() {
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT))
                .isFalse();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.ADAPTER_ERROR))
                .isFalse();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CALLBACK_FAILED))
                .isFalse();
        assertThat(ReconGates.needsChannelReview(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.MANUAL))
                .isFalse();
    }

    @Test
    void autoRefulfillOnlyChannelRejectOrAdapterError() {
        assertThat(ReconGates.autoRefulfillEligible(
                        true,
                        ReconPolicies.AUTO,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT))
                .isTrue();
        assertThat(ReconGates.autoRefulfillEligible(
                        true,
                        ReconPolicies.AUTO,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.ADAPTER_ERROR))
                .isTrue();
        assertThat(ReconGates.autoRefulfillEligible(
                        true,
                        ReconPolicies.AUTO,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CALLBACK_FAILED))
                .isFalse();
        assertThat(ReconGates.autoRefulfillEligible(
                        true,
                        ReconPolicies.AUTO,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.TIMEOUT))
                .isFalse();
        assertThat(ReconGates.autoRefulfillEligible(
                        false,
                        ReconPolicies.AUTO,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT))
                .isFalse();
        assertThat(ReconGates.autoRefulfillEligible(
                        true,
                        ReconPolicies.REVIEW,
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT))
                .isFalse();
    }

    @Test
    void actionAllowSet() {
        assertThat(ReconGates.allowedActions(
                        ReconResults.MATCHED, GrantRecordStatuses.FULFILL_ARRIVED, null, ReconReviewStatuses.NONE))
                .isEmpty();
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_ARRIVED,
                        null,
                        ReconReviewStatuses.NONE))
                .containsExactly(ReconActions.ABSORB);
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_SENDING,
                        null,
                        ReconReviewStatuses.NONE))
                .isEmpty();
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_SENDING,
                        null,
                        ReconReviewStatuses.CONFIRMED))
                .containsExactlyInAnyOrder(ReconActions.REFULFILL, ReconActions.MANUAL_GRANT);
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.TIMEOUT,
                        ReconReviewStatuses.NONE))
                .isEmpty();
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT,
                        ReconReviewStatuses.NONE))
                .containsExactlyInAnyOrder(ReconActions.REFULFILL, ReconActions.MANUAL_GRANT);
        assertThat(ReconGates.allowedActions(
                        ReconResults.CHANNEL_ONLY, null, null, ReconReviewStatuses.NONE))
                .containsExactlyInAnyOrder(ReconActions.LEDGER_ONLY, ReconActions.ABSORB);
        assertThat(ReconGates.allowedActions(
                        ReconResults.AMOUNT_MISMATCH,
                        GrantRecordStatuses.FULFILL_ARRIVED,
                        null,
                        ReconReviewStatuses.NONE))
                .containsExactly(ReconActions.ABSORB);
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT,
                        ReconReviewStatuses.REJECTED))
                .containsExactly(ReconActions.ABSORB);
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CALLBACK_FAILED,
                        ReconReviewStatuses.NONE))
                .isEmpty();
        assertThat(ReconGates.allowedActions(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.MANUAL,
                        ReconReviewStatuses.NONE))
                .isEmpty();
    }

    @Test
    void reviewRequiredForTimeoutAndSending() {
        assertThat(ReconGates.reviewRequiredFor(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.TIMEOUT,
                        ReconActions.REFULFILL))
                .isTrue();
        assertThat(ReconGates.reviewRequiredFor(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.TIMEOUT,
                        ReconActions.MANUAL_GRANT))
                .isTrue();
        assertThat(ReconGates.reviewRequiredFor(
                        ReconResults.PLATFORM_ONLY,
                        GrantRecordStatuses.FULFILL_FAILED,
                        FulfillFailReasons.CHANNEL_REJECT,
                        ReconActions.REFULFILL))
                .isFalse();
    }
}
