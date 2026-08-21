package com.mkt.activity.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import org.junit.jupiter.api.Test;

class ActivityErrorCodesTest {

    @Test
    void codesAreValidThreeSegment() {
        for (ActivityErrorCodes code : ActivityErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).as(code.code()).isTrue();
            assertThat(code.httpStatus()).isGreaterThanOrEqualTo(400);
            assertThat(code.message()).isNotBlank();
        }
    }

    @Test
    void settingsPermissionsOperatorAndDuplicate() {
        ActivitySettings settings = new ActivitySettings();
        settings.setNewUserDays(9);
        settings.setPortalWritePerSecond(4);
        assertThat(settings.newUserDays()).isEqualTo(9);
        assertThat(settings.portalWritePerSecond()).isEqualTo(4);
        assertThat(ActivityPermissions.QUERY).isEqualTo("activity:query");
        assertThat(ActivityPermissions.PARTICIPATION_QUERY).isEqualTo("activity:participation:query");
        UserContext.set(new UserPrincipal(8L, "admin", "op"));
        try {
            assertThat(ActivityOperator.requireUserId()).isEqualTo(8L);
            assertThat(ActivityOperator.optionalUserId()).isEqualTo(8L);
        } finally {
            UserContext.clear();
        }
        assertThat(ActivityOperator.optionalUserId()).isNull();
        RuntimeException wrapped =
                new RuntimeException(new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry"));
        assertThat(ActivityDuplicateKeys.duplicate(wrapped)).isTrue();
        assertThat(ActivityDuplicateKeys.duplicate(new RuntimeException("other"))).isFalse();
    }
}
