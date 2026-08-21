package com.mkt.activity.support;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;

public final class ActivityOperator {

    private ActivityOperator() {}

    public static long requireUserId() {
        UserPrincipal principal = UserContext.current()
                .orElseThrow(() -> new BusinessException(CommonErrorCodes.PERMISSION_DENIED));
        if (principal.userId() == null) {
            throw new BusinessException(CommonErrorCodes.PERMISSION_DENIED);
        }
        return principal.userId();
    }

    public static Long optionalUserId() {
        return UserContext.current().map(UserPrincipal::userId).orElse(null);
    }
}
