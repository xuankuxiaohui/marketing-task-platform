package com.marketing.task.context;

public final class SimulateContextHolder {
    private static final ThreadLocal<UserContext> SIMULATE = new ThreadLocal<>();

    public static void set(UserContext ctx) { SIMULATE.set(ctx); }
    public static UserContext get() { return SIMULATE.get(); }
    public static void clear() { SIMULATE.remove(); }
    public static boolean isSimulating() { return SIMULATE.get() != null; }

    private SimulateContextHolder() {}
}
