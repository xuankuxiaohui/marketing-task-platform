package com.mkt.task.support;

/** Appendix A defaults. Mutable so tests can flip limits without identity ConfigService. */
public class TaskSettings {

    public static final int DEFAULT_STEP_MAX_COUNT = 50;
    public static final int DEFAULT_CROWD_MAX_SIZE = 100_000;
    public static final int DEFAULT_DAILY_LIMIT = 20;
    public static final int DEFAULT_EXPIRE_AFTER_WINDOW_DAYS = 7;
    public static final int DEFAULT_PORTAL_WRITE_PER_SECOND = 10;

    private int stepMaxCount = DEFAULT_STEP_MAX_COUNT;
    private int crowdMaxSize = DEFAULT_CROWD_MAX_SIZE;
    private int dailyLimitPerUser = DEFAULT_DAILY_LIMIT;
    private int expireAfterWindowDays = DEFAULT_EXPIRE_AFTER_WINDOW_DAYS;
    private int portalWritePerSecond = DEFAULT_PORTAL_WRITE_PER_SECOND;

    public int stepMaxCount() {
        return stepMaxCount;
    }

    public void setStepMaxCount(int stepMaxCount) {
        this.stepMaxCount = stepMaxCount;
    }

    public int crowdMaxSize() {
        return crowdMaxSize;
    }

    public void setCrowdMaxSize(int crowdMaxSize) {
        this.crowdMaxSize = crowdMaxSize;
    }

    public int dailyLimitPerUser() {
        return dailyLimitPerUser;
    }

    public void setDailyLimitPerUser(int dailyLimitPerUser) {
        this.dailyLimitPerUser = dailyLimitPerUser;
    }

    public int expireAfterWindowDays() {
        return expireAfterWindowDays;
    }

    public void setExpireAfterWindowDays(int expireAfterWindowDays) {
        this.expireAfterWindowDays = expireAfterWindowDays;
    }

    public int portalWritePerSecond() {
        return portalWritePerSecond;
    }

    public void setPortalWritePerSecond(int portalWritePerSecond) {
        this.portalWritePerSecond = portalWritePerSecond;
    }
}
