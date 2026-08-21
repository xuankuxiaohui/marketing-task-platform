package com.mkt.reward.support;

/** Appendix A grant retry defaults. Mutable so tests can flip limits without identity ConfigService. */
public class RewardGrantSettings {

    public static final int DEFAULT_RETRY_MAX = 3;
    public static final int DEFAULT_RETRY_INTERVAL_SECONDS = 30;

    private int retryMax = DEFAULT_RETRY_MAX;
    private int retryIntervalSeconds = DEFAULT_RETRY_INTERVAL_SECONDS;

    public int retryMax() {
        return retryMax;
    }

    public void setRetryMax(int retryMax) {
        this.retryMax = retryMax;
    }

    public int retryIntervalSeconds() {
        return retryIntervalSeconds;
    }

    public void setRetryIntervalSeconds(int retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
    }
}
