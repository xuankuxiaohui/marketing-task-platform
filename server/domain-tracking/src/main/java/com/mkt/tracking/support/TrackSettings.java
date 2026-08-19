package com.mkt.tracking.support;

import com.mkt.tracking.domain.DisabledEventPolicy;
import com.mkt.tracking.domain.UnregisteredPolicy;

/**
 * Appendix A defaults. Mutable so tests can flip policy without identity ConfigService (RL-11 /
 * task 24).
 */
public final class TrackSettings {

    private volatile int batchMaxSize = 50;
    private volatile int eventMaxPayloadKb = 8;
    private volatile UnregisteredPolicy unregisteredPolicy = UnregisteredPolicy.ACCEPT;
    private volatile DisabledEventPolicy disabledEventPolicy = DisabledEventPolicy.DROP_COUNT;
    private volatile int rateLimitPerMinute = 60;
    private volatile int retentionEventDays = 90;

    public int batchMaxSize() {
        return batchMaxSize;
    }

    public void setBatchMaxSize(int batchMaxSize) {
        this.batchMaxSize = batchMaxSize;
    }

    public int eventMaxPayloadKb() {
        return eventMaxPayloadKb;
    }

    public void setEventMaxPayloadKb(int eventMaxPayloadKb) {
        this.eventMaxPayloadKb = eventMaxPayloadKb;
    }

    public int eventMaxPayloadBytes() {
        return eventMaxPayloadKb * 1024;
    }

    public UnregisteredPolicy unregisteredPolicy() {
        return unregisteredPolicy;
    }

    public void setUnregisteredPolicy(UnregisteredPolicy unregisteredPolicy) {
        this.unregisteredPolicy = unregisteredPolicy == null ? UnregisteredPolicy.ACCEPT : unregisteredPolicy;
    }

    public DisabledEventPolicy disabledEventPolicy() {
        return disabledEventPolicy;
    }

    public void setDisabledEventPolicy(DisabledEventPolicy disabledEventPolicy) {
        this.disabledEventPolicy =
                disabledEventPolicy == null ? DisabledEventPolicy.DROP_COUNT : disabledEventPolicy;
    }

    public int rateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int retentionEventDays() {
        return retentionEventDays;
    }

    public void setRetentionEventDays(int retentionEventDays) {
        this.retentionEventDays = retentionEventDays;
    }
}
