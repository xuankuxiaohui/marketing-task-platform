package com.mkt.reward.support;

/** Appendix A claim / fulfill / recon defaults. Mutable so tests can flip without ConfigService. */
public class RewardRuntimeSettings {

    public static final int DEFAULT_CLAIM_RETRY_MAX = 3;
    public static final int DEFAULT_CLAIMING_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_FULFILL_RETRY_MAX = 3;
    public static final int DEFAULT_FULFILL_RETRY_INTERVAL_SECONDS = 30;
    public static final int DEFAULT_SENDING_TIMEOUT_HOURS = 24;
    public static final boolean DEFAULT_AUTO_REFULFILL_ENABLED = false;

    private int claimRetryMax = DEFAULT_CLAIM_RETRY_MAX;
    private int claimingTimeoutSeconds = DEFAULT_CLAIMING_TIMEOUT_SECONDS;
    private int fulfillRetryMax = DEFAULT_FULFILL_RETRY_MAX;
    private int fulfillRetryIntervalSeconds = DEFAULT_FULFILL_RETRY_INTERVAL_SECONDS;
    private int sendingTimeoutHours = DEFAULT_SENDING_TIMEOUT_HOURS;
    private boolean autoRefulfillEnabled = DEFAULT_AUTO_REFULFILL_ENABLED;

    public int claimRetryMax() {
        return claimRetryMax;
    }

    public void setClaimRetryMax(int claimRetryMax) {
        this.claimRetryMax = claimRetryMax;
    }

    public int claimingTimeoutSeconds() {
        return claimingTimeoutSeconds;
    }

    public void setClaimingTimeoutSeconds(int claimingTimeoutSeconds) {
        this.claimingTimeoutSeconds = claimingTimeoutSeconds;
    }

    public int fulfillRetryMax() {
        return fulfillRetryMax;
    }

    public void setFulfillRetryMax(int fulfillRetryMax) {
        this.fulfillRetryMax = fulfillRetryMax;
    }

    public int fulfillRetryIntervalSeconds() {
        return fulfillRetryIntervalSeconds;
    }

    public void setFulfillRetryIntervalSeconds(int fulfillRetryIntervalSeconds) {
        this.fulfillRetryIntervalSeconds = fulfillRetryIntervalSeconds;
    }

    public int sendingTimeoutHours() {
        return sendingTimeoutHours;
    }

    public void setSendingTimeoutHours(int sendingTimeoutHours) {
        this.sendingTimeoutHours = sendingTimeoutHours;
    }

    public boolean autoRefulfillEnabled() {
        return autoRefulfillEnabled;
    }

    public void setAutoRefulfillEnabled(boolean autoRefulfillEnabled) {
        this.autoRefulfillEnabled = autoRefulfillEnabled;
    }
}
