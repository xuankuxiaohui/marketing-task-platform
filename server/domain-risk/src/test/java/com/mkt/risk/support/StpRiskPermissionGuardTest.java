package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import org.junit.jupiter.api.Test;

class StpRiskPermissionGuardTest {

    @Test
    void missingPermissionIsDenied() {
        StpRiskPermissionGuard guard =
                new StpRiskPermissionGuard(permission -> {
                    throw new NotPermissionException(permission);
                });
        assertDenied(() -> guard.require(RiskListPermissions.BLACK_QUERY));
    }

    @Test
    void notLoggedInIsDenied() {
        StpRiskPermissionGuard guard =
                new StpRiskPermissionGuard(permission -> {
                    throw new SaTokenException("not login");
                });
        assertDenied(() -> guard.require(RiskListPermissions.BLACK_ADD));
    }

    @Test
    void missingSaContextIsDenied() {
        StpRiskPermissionGuard guard =
                new StpRiskPermissionGuard(permission -> {
                    throw new IllegalStateException("sa-token context missing");
                });
        assertDenied(() -> guard.require(RiskListPermissions.BLACK_QUERY));
    }

    @Test
    void passesWhenCheckerAccepts() {
        StpRiskPermissionGuard guard = new StpRiskPermissionGuard(permission -> {
            /* logged in with permission */
        });
        guard.require(RiskListPermissions.WHITE_QUERY);
    }

    private static void assertDenied(Runnable action) {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(action::run)
                .satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCodes.PERMISSION_DENIED));
    }
}
