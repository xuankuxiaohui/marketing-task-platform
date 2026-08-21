package com.mkt.reward.domain;

import java.util.Set;

/** Match / review / action gates (design §5.11). Unlisted combinations are forbidden. */
public final class ReconGates {

    private ReconGates() {}

    public static boolean needsChannelReview(String result, String fulfillment, String failReason) {
        if (!ReconResults.PLATFORM_ONLY.equals(result)) {
            return false;
        }
        if (GrantRecordStatuses.FULFILL_SENDING.equals(fulfillment)) {
            return true;
        }
        if (!GrantRecordStatuses.FULFILL_FAILED.equals(fulfillment)) {
            return false;
        }
        return failReason == null || failReason.isBlank() || !FulfillFailReasons.closed(failReason)
                || FulfillFailReasons.TIMEOUT.equals(failReason);
    }

    public static boolean autoRefulfillEligible(
            boolean autoEnabled,
            String effectivePolicy,
            String result,
            String fulfillment,
            String failReason) {
        return autoEnabled
                && ReconPolicies.AUTO.equals(effectivePolicy)
                && ReconResults.PLATFORM_ONLY.equals(result)
                && GrantRecordStatuses.FULFILL_FAILED.equals(fulfillment)
                && failReason != null
                && FulfillFailReasons.AUTO_REFULFILL.contains(failReason);
    }

    public static Set<String> allowedActions(
            String result, String fulfillment, String failReason, String reviewStatus) {
        if (ReconReviewStatuses.REJECTED.equals(reviewStatus)) {
            return Set.of(ReconActions.ABSORB);
        }
        if (ReconResults.MATCHED.equals(result)) {
            return Set.of();
        }
        if (ReconResults.AMOUNT_MISMATCH.equals(result)) {
            return Set.of(ReconActions.ABSORB);
        }
        if (ReconResults.CHANNEL_ONLY.equals(result)) {
            return Set.of(ReconActions.LEDGER_ONLY, ReconActions.ABSORB);
        }
        if (!ReconResults.PLATFORM_ONLY.equals(result)) {
            return Set.of();
        }
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(fulfillment)) {
            return Set.of(ReconActions.ABSORB);
        }
        if (GrantRecordStatuses.FULFILL_SENDING.equals(fulfillment)) {
            return confirmed(reviewStatus) ? Set.of(ReconActions.REFULFILL, ReconActions.MANUAL_GRANT) : Set.of();
        }
        if (GrantRecordStatuses.FULFILL_FAILED.equals(fulfillment)) {
            if (FulfillFailReasons.TIMEOUT.equals(failReason)
                    || failReason == null
                    || failReason.isBlank()
                    || !FulfillFailReasons.closed(failReason)) {
                return confirmed(reviewStatus) ? Set.of(ReconActions.REFULFILL, ReconActions.MANUAL_GRANT) : Set.of();
            }
            if (FulfillFailReasons.CHANNEL_REJECT.equals(failReason)
                    || FulfillFailReasons.ADAPTER_ERROR.equals(failReason)) {
                return Set.of(ReconActions.REFULFILL, ReconActions.MANUAL_GRANT);
            }
        }
        return Set.of();
    }

    public static boolean reviewRequiredFor(String result, String fulfillment, String failReason, String action) {
        if (!ReconActions.REFULFILL.equals(action) && !ReconActions.MANUAL_GRANT.equals(action)) {
            return false;
        }
        return needsChannelReview(result, fulfillment, failReason);
    }

    private static boolean confirmed(String reviewStatus) {
        return ReconReviewStatuses.CONFIRMED.equals(reviewStatus);
    }
}
