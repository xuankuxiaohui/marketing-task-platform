package com.mkt.reward.domain;

/** C-end prize list tabs (design §4.9.3). */
public final class PrizeTabs {

    public static final String PENDING = "PENDING";
    public static final String ALL = "ALL";

    private PrizeTabs() {}

    public static boolean pendingVisible(String status, String fulfillmentStatus) {
        if (GrantRecordStatuses.WON.equals(status)
                || GrantRecordStatuses.CLAIMING.equals(status)
                || GrantRecordStatuses.RETRY_PENDING.equals(status)) {
            return true;
        }
        return GrantRecordStatuses.GRANTED.equals(status)
                && (GrantRecordStatuses.FULFILL_SENDING.equals(fulfillmentStatus)
                        || GrantRecordStatuses.FULFILL_FAILED.equals(fulfillmentStatus));
    }
}
