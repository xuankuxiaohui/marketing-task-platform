package com.mkt.task.domain;

import java.util.List;

/** Visibility outcome with closed reason chain (design §5.3). */
public record VisibilityResult(boolean visible, List<VisibilityReason> reasons) {

    /** Shared allow outcome — list path must not allocate per published task. */
    public static final VisibilityResult ALLOW = new VisibilityResult(true, List.of());

    public VisibilityResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static VisibilityResult allow() {
        return ALLOW;
    }

    public static VisibilityResult deny(List<VisibilityReason> reasons) {
        return new VisibilityResult(false, reasons);
    }
}
