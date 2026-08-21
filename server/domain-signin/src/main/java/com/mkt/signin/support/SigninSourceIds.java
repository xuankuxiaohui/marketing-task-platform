package com.mkt.signin.support;

import java.time.LocalDate;

public final class SigninSourceIds {

    private SigninSourceIds() {}

    /** Per-activity per-user per-tier; restreak does not re-grant (R21.2). */
    public static String grantTier(long activityId, long userId, int day) {
        return activityId + ":" + userId + ":" + day;
    }

    public static String catchupConsume(long activityId, long userId, LocalDate signDate) {
        return activityId + ":" + userId + ":" + signDate;
    }
}
