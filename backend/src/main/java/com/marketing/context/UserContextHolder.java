package com.marketing.context;

import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;

public final class UserContextHolder {
    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        UserContext context = HOLDER.get();
        if (context == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
