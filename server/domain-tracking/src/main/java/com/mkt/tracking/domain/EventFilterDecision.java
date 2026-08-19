package com.mkt.tracking.domain;

public enum EventFilterDecision {
    ACCEPT,
    DROP_MALFORMED,
    DROP_UNREGISTERED,
    DROP_DISABLED
}
