package com.mkt.signin.support;

/** Appendix A signin.catchup.* defaults. Mutable for tests. */
public class SigninSettings {

    public static final int DEFAULT_WINDOW_DAYS = 7;
    public static final int DEFAULT_DAILY_LIMIT = 1;
    public static final int DEFAULT_COST_POINTS = 100;
    public static final int DEFAULT_PORTAL_WRITE_PER_SECOND = 10;

    private int windowDays = DEFAULT_WINDOW_DAYS;
    private int dailyLimit = DEFAULT_DAILY_LIMIT;
    private int costPoints = DEFAULT_COST_POINTS;
    private int portalWritePerSecond = DEFAULT_PORTAL_WRITE_PER_SECOND;

    public int windowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public int costPoints() {
        return costPoints;
    }

    public void setCostPoints(int costPoints) {
        this.costPoints = costPoints;
    }

    public int portalWritePerSecond() {
        return portalWritePerSecond;
    }

    public void setPortalWritePerSecond(int portalWritePerSecond) {
        this.portalWritePerSecond = portalWritePerSecond;
    }
}
