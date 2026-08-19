package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RiskOperatorTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void requireUserIdReturnsPrincipal() {
        UserContext.set(new UserPrincipal(3L, "admin", "op"));
        assertThat(RiskOperator.requireUserId()).isEqualTo(3L);
    }

    @Test
    void emptyContextIsPermissionDenied() {
        assertThatThrownBy(RiskOperator::requireUserId)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(CommonErrorCodes.PERMISSION_DENIED));
    }
}
