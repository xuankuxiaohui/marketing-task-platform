package com.mkt.reward.domain;

import java.util.Set;

/** Statuses that occupy a claim-limit slot (design §5.7). */
public final class GrantRecordStatuses {

    public static final String PENDING = "PENDING";
    public static final String WON = "WON";
    public static final String CLAIMING = "CLAIMING";
    public static final String GRANTED = "GRANTED";
    public static final String RETRY_PENDING = "RETRY_PENDING";
    public static final String PERMANENT_FAILED = "PERMANENT_FAILED";
    public static final String EXPIRED = "EXPIRED";

    public static final Set<String> LIMIT_COUNT = Set.of(GRANTED, WON, CLAIMING, RETRY_PENDING);

    public static final String FULFILL_NONE = "NONE";
    public static final String FULFILL_SENDING = "SENDING";
    public static final String FULFILL_ARRIVED = "ARRIVED";
    public static final String FULFILL_FAILED = "FULFILL_FAILED";

    public static final String RECON_NONE = "NONE";

    private GrantRecordStatuses() {}
}
