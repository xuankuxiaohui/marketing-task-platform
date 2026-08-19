package com.mkt.contract;

/** Fulfillment four-state (design §2.2.3 / §3.4.2). */
public enum FulfillmentStatus {
    NONE,
    SENDING,
    ARRIVED,
    FULFILL_FAILED
}
