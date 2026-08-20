package com.mkt.task.domain;

/** Closed miss reasons (design §5.3). */
public enum VisibilityReason {
    NOT_IN_WINDOW,
    GRAY_MISS,
    FILTER_MISS,
    OFFLINE
}
