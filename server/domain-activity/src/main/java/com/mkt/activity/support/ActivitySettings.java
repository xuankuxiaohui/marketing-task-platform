package com.mkt.activity.support;

/** Appendix A activity.new-user-days default. Mutable for tests. */
public class ActivitySettings {

    public static final int DEFAULT_NEW_USER_DAYS = 7;
    public static final int DEFAULT_PORTAL_WRITE_PER_SECOND = 10;

    private int newUserDays = DEFAULT_NEW_USER_DAYS;
    private int portalWritePerSecond = DEFAULT_PORTAL_WRITE_PER_SECOND;

    public int newUserDays() {
        return newUserDays;
    }

    public void setNewUserDays(int newUserDays) {
        this.newUserDays = newUserDays;
    }

    public int portalWritePerSecond() {
        return portalWritePerSecond;
    }

    public void setPortalWritePerSecond(int portalWritePerSecond) {
        this.portalWritePerSecond = portalWritePerSecond;
    }
}
