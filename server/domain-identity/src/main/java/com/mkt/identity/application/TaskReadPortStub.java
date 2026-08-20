package com.mkt.identity.application;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.TaskReadPort;

/** Placeholder when domain-task is not on the classpath. */
public class TaskReadPortStub implements TaskReadPort {

    @Override
    public InstanceCounts instanceCounts(long userId) {
        return new InstanceCounts(0L, 0L);
    }
}
