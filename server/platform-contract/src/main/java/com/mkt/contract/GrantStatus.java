package com.mkt.contract;

/** Claim/grant seven-state machine (design §3.4.2). */
public enum GrantStatus {
    PENDING,
    WON,
    CLAIMING,
    GRANTED,
    RETRY_PENDING,
    PERMANENT_FAILED,
    EXPIRED
}
