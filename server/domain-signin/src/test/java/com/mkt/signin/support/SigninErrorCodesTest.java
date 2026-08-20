package com.mkt.signin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import org.junit.jupiter.api.Test;

class SigninErrorCodesTest {

    @Test
    void codesAreValidThreeSegment() {
        for (SigninErrorCodes code : SigninErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).as(code.code()).isTrue();
            assertThat(code.httpStatus()).isGreaterThanOrEqualTo(400);
            assertThat(code.message()).isNotBlank();
        }
    }

    @Test
    void sourceIdsAreStable() {
        assertThat(SigninSourceIds.grantTier(3, 9, 1)).isEqualTo("3:9:1");
        assertThat(SigninSourceIds.catchupConsume(3, 9, java.time.LocalDate.of(2026, 8, 19)))
                .isEqualTo("3:9:2026-08-19");
    }

    @Test
    void settingsAndPermissionsAndOperator() {
        SigninSettings settings = new SigninSettings();
        settings.setWindowDays(5);
        settings.setDailyLimit(2);
        settings.setCostPoints(50);
        settings.setPortalWritePerSecond(3);
        assertThat(settings.windowDays()).isEqualTo(5);
        assertThat(settings.dailyLimit()).isEqualTo(2);
        assertThat(settings.costPoints()).isEqualTo(50);
        assertThat(settings.portalWritePerSecond()).isEqualTo(3);
        assertThat(SigninPermissions.CONFIG_QUERY).isEqualTo("signin:config:query");
        assertThat(SigninPermissions.RECORD_QUERY).isEqualTo("signin:record:query");
        UserContext.set(new UserPrincipal(8L, "admin", "op"));
        try {
            assertThat(SigninOperator.requireUserId()).isEqualTo(8L);
            assertThat(SigninOperator.optionalUserId()).isEqualTo(8L);
        } finally {
            UserContext.clear();
        }
        assertThat(SigninOperator.optionalUserId()).isNull();
    }

    @Test
    void duplicateKeyDetectionWalksCause() {
        RuntimeException wrapped =
                new RuntimeException(new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry"));
        assertThat(SigninDuplicateKeys.duplicate(wrapped)).isTrue();
        assertThat(SigninDuplicateKeys.duplicate(new RuntimeException("other"))).isFalse();
    }
}
