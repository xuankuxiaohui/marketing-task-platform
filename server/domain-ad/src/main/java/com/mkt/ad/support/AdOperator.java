package com.mkt.ad.support;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;

public final class AdOperator {

    private AdOperator() {}

    public static long requireUserId() {
        UserPrincipal principal = UserContext.current()
                .orElseThrow(() -> new BusinessException(CommonErrorCodes.PERMISSION_DENIED));
        if (principal.userId() == null) {
            throw new BusinessException(CommonErrorCodes.PERMISSION_DENIED);
        }
        return principal.userId();
    }
}
