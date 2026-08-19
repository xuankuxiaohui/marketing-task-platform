package com.mkt.risk.support;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import org.springframework.stereotype.Component;

/**
 * List-type permission check. Unauthenticated, forbidden, or Sa-Token failures deny.
 * Task 21 should map {@link NotLoginException} to {@code auth.session.invalid} (401).
 */
@Component
public class StpRiskPermissionGuard implements RiskPermissionGuard {

    @FunctionalInterface
    interface Checker {
        void check(String permission);
    }

    private final Checker checker;

    public StpRiskPermissionGuard() {
        this(StpUtil::checkPermission);
    }

    StpRiskPermissionGuard(Checker checker) {
        this.checker = checker;
    }

    @Override
    public void require(String permission) {
        try {
            checker.check(permission);
        } catch (SaTokenException | IllegalStateException ex) {
            throw new BusinessException(CommonErrorCodes.PERMISSION_DENIED, ex);
        }
    }
}
