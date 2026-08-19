package com.mkt.infra.outbox;

import java.time.Duration;

/** 10s, 30s, 2m, 10m, 30m (design §6.4). */
public final class OutboxBackoff {

    private static final Duration[] STEPS = {
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(10),
        Duration.ofMinutes(30)
    };

    public static final int DEAD_AFTER = 5;

    private OutboxBackoff() {
    }

    public static Duration delayAfterFailure(int retryCountAfterIncrement) {
        int index = Math.min(retryCountAfterIncrement, STEPS.length) - 1;
        return STEPS[Math.max(index, 0)];
    }

    public static boolean dead(int retryCountAfterIncrement) {
        return retryCountAfterIncrement >= DEAD_AFTER;
    }
}
