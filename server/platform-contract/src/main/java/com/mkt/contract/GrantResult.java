package com.mkt.contract;

/** Result of {@link RewardPort#grant} (design §2.2.3). */
public record GrantResult(
        long recordId,
        GrantStatus status,
        FulfillmentStatus fulfillmentStatus,
        long prizeId,
        boolean hitIdempotent) {
}
