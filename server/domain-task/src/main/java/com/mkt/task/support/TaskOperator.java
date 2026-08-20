package com.mkt.task.support;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;

/** Resolves the admin operator without turning an empty context into HTTP 500. */
public final class TaskOperator {

    private TaskOperator() {}

    public static long requireUserId() {
        UserPrincipal principal = UserContext.current()
                .orElseThrow(() -> new BusinessException(CommonErrorCodes.PERMISSION_DENIED));
        if (principal.userId() == null) {
            throw new BusinessException(CommonErrorCodes.PERMISSION_DENIED);
        }
        return principal.userId();
    }
}
