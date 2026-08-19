package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ErrorCodeFormatTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "auth.login.locked",
                "task.claim.mutex-blocked",
                "reward.stock.insufficient",
                "points.account.insufficient-balance",
                "risk.blocked.generic",
                "track.batch.overflow",
                "internal.sign.invalid-signature",
                "dict.entry.duplicate-value",
                "config.value.unknown",
                "cache.namespace.not-found",
                "system.cache.session-forbidden",
                "ad.position.not-found",
                "signin.signin.duplicate-day",
                "activity.activity.not-found"
            })
    void acceptsThreeSegmentCodes(String code) {
        assertThat(ErrorCodeFormat.isValid(code)).isTrue();
        ErrorCodeFormat.requireValid(code);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "common.param-invalid",
                "common.permission-denied",
                "common.rate-limited",
                "common.not-found",
                "common.server-error"
            })
    void acceptsClosedCommonCodes(String code) {
        assertThat(ErrorCodeFormat.isValid(code)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "TASK_CLAIM_MUTEX",
                "10001",
                "error.task.claim.mutex-blocked",
                "task.claim",
                "task.claim.mutex.blocked",
                "Task.claim.mutex-blocked",
                "order.pay.failed",
                "sys.user.not-found",
                "auth.unknown.x",
                "common.oops",
                "common.param-invalid.extra"
            })
    void rejectsInvalidCodes(String code) {
        assertThat(ErrorCodeFormat.isValid(code)).isFalse();
        assertThatThrownBy(() -> ErrorCodeFormat.requireValid(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(code);
    }

    @Test
    void rejectsNull() {
        assertThat(ErrorCodeFormat.isValid(null)).isFalse();
    }

    @Test
    void commonErrorCodesAreValid() {
        for (CommonErrorCodes code : CommonErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).isTrue();
            assertThat(code.httpStatus()).isGreaterThanOrEqualTo(400);
            assertThat(code.message()).isNotBlank();
        }
    }
}
