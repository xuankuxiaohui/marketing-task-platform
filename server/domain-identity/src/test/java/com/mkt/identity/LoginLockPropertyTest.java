package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.domain.LoginLock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R1.1: lock fires exactly on the 5th consecutive failure; success resets; lock does not count.
 */
class LoginLockPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(LoginLockPropertyTest.class);
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    enum Op {
        SUCCESS,
        FAILURE
    }

    @Property(tries = 200)
    void fifthConsecutiveFailureLocksFifteenMinutes(@ForAll("traces") List<Op> ops) {
        log.debug("LoginLockPropertyTest trace size={}", ops.size());
        LoginLock.State state = LoginLock.idle();
        Instant now = NOW;
        int consecutive = 0;
        for (Op op : ops) {
            if (state.locked(now)) {
                LoginLock.State afterFail = LoginLock.onFailure(state, now);
                LoginLock.State afterSuccess = LoginLock.onSuccess(state, now);
                assertThat(afterFail).isEqualTo(state);
                assertThat(afterSuccess).isEqualTo(state);
                assertThat(state.remainingMinutes(now)).isEqualTo(15);
                continue;
            }
            if (op == Op.FAILURE) {
                LoginLock.State next = LoginLock.onFailure(state, now);
                consecutive++;
                if (consecutive == LoginLock.THRESHOLD) {
                    assertThat(next.locked(now)).isTrue();
                    assertThat(next.remainingMinutes(now)).isEqualTo(15);
                } else if (consecutive < LoginLock.THRESHOLD) {
                    assertThat(next.locked(now)).isFalse();
                    assertThat(next.failedAttempts()).isEqualTo(consecutive);
                }
                state = next;
            } else {
                state = LoginLock.onSuccess(state, now);
                consecutive = 0;
                assertThat(state.failedAttempts()).isZero();
                assertThat(state.locked(now)).isFalse();
            }
        }
    }

    @Property(tries = 200)
    void lockExpiryZerosAttemptsAndDoesNotImmediatelyRelock(
            @ForAll @IntRange(min = 0, max = 4) int failuresWhileLocked,
            @ForAll @IntRange(min = 0, max = 3) int failuresAfterUnlock) {
        Instant now = NOW;
        LoginLock.State state = LoginLock.idle();
        for (int i = 0; i < LoginLock.THRESHOLD; i++) {
            state = LoginLock.onFailure(state, now);
        }
        assertThat(state.locked(now)).isTrue();
        for (int i = 0; i < failuresWhileLocked; i++) {
            Instant during = now.plus(Duration.ofMinutes(i + 1));
            LoginLock.State next = LoginLock.onFailure(state, during);
            assertThat(next).isEqualTo(state);
            assertThat(next.locked(during)).isTrue();
        }
        Instant expired = now.plus(LoginLock.LOCK_DURATION);
        assertThat(state.locked(expired)).isFalse();
        LoginLock.State first = LoginLock.onFailure(state, expired);
        assertThat(first.locked(expired)).isFalse();
        assertThat(first.failedAttempts()).isEqualTo(1);
        state = first;
        for (int i = 0; i < failuresAfterUnlock; i++) {
            Instant later = expired.plusSeconds(i + 1L);
            state = LoginLock.onFailure(state, later);
            assertThat(state.failedAttempts()).isEqualTo(2 + i);
            assertThat(state.locked(later)).isFalse();
        }
        log.debug(
                "lockExpiry zeros attempts whileLocked={} afterUnlock={}",
                failuresWhileLocked,
                failuresAfterUnlock);
    }

    @Provide
    Arbitrary<List<Op>> traces() {
        Arbitrary<List<Op>> prefix = Arbitraries.of(Op.class).list().ofMinSize(0).ofMaxSize(4);
        Arbitrary<List<Op>> failRun = Arbitraries.of(Op.FAILURE).list().ofMinSize(5).ofMaxSize(7);
        Arbitrary<List<Op>> suffix = Arbitraries.of(Op.class).list().ofMinSize(0).ofMaxSize(3);
        return Combinators.combine(prefix, failRun, suffix).as((a, b, c) -> {
            List<Op> all = new ArrayList<>(a.size() + b.size() + c.size());
            all.addAll(a);
            all.addAll(b);
            all.addAll(c);
            return all.size() <= 12 ? all : all.subList(0, 12);
        });
    }
}
