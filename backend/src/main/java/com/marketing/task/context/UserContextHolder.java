package com.marketing.task.context;

import com.marketing.task.common.BusinessException;

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
            throw new BusinessException(401, "缺少用户上下文");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
