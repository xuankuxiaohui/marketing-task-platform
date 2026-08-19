package com.mkt.task.support;

/** Appendix A defaults. Mutable so tests can flip limits without identity ConfigService. */
public class TaskSettings {

    public static final int DEFAULT_STEP_MAX_COUNT = 50;
    public static final int DEFAULT_CROWD_MAX_SIZE = 100_000;

    private int stepMaxCount = DEFAULT_STEP_MAX_COUNT;
    private int crowdMaxSize = DEFAULT_CROWD_MAX_SIZE;

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
}
