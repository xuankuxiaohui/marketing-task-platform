package com.mkt.infra.degrade;

/** Rows of design §6.8. */
public enum DegradeComponent {
    SESSION,
    RATE_LIMIT,
    CACHE,
    DISTRIBUTED_LOCK,
    CLAIM_LOCK,
    RISK,
    OUTBOX_RELAY,
    NONCE,
    TRACKING
}
