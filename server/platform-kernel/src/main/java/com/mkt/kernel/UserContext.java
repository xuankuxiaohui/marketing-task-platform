package com.mkt.kernel;

import java.util.Optional;

/** Request-scoped principal holder. Cleared by the trace filter. */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static Optional<UserPrincipal> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static UserPrincipal require() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new IllegalStateException("UserContext is empty");
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
