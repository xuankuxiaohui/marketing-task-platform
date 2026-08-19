package com.mkt.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** R1.3 / R1.4 lock: 5 consecutive failures → 15 minutes; success resets. */
public final class LoginLock {

    public static final int THRESHOLD = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LoginLock() {
    }

    public record State(int failedAttempts, Instant lockedUntil) {

        public State {
            if (failedAttempts < 0) {
                throw new IllegalArgumentException("failedAttempts < 0");
            }
        }

        public boolean locked(Instant now) {
            return lockedUntil != null && lockedUntil.isAfter(Objects.requireNonNull(now, "now"));
        }

        public long remainingMinutes(Instant now) {
            if (!locked(now)) {
                return 0L;
            }
            long seconds = Duration.between(now, lockedUntil).getSeconds();
            return Math.max(1L, (seconds + 59) / 60);
        }
    }

    public static State idle() {
        return new State(0, null);
    }

    public static State of(int failedAttempts, Instant lockedUntil) {
        return new State(failedAttempts, lockedUntil);
    }

    public static State onFailure(State current, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        if (current.locked(now)) {
            return current;
        }
        int next = current.failedAttempts() + 1;
        if (next >= THRESHOLD) {
            return new State(next, now.plus(LOCK_DURATION));
        }
        return new State(next, null);
    }

    public static State onSuccess(State current, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        if (current.locked(now)) {
            return current;
        }
        return idle();
    }
}
