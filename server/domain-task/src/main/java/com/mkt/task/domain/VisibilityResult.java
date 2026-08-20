package com.mkt.task.domain;

import java.util.List;

/** Visibility outcome with closed reason chain (design §5.3). */
public record VisibilityResult(boolean visible, List<VisibilityReason> reasons) {

    public VisibilityResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static VisibilityResult allow() {
        return new VisibilityResult(true, List.of());
    }

    public static VisibilityResult deny(List<VisibilityReason> reasons) {
        return new VisibilityResult(false, reasons);
    }
}
