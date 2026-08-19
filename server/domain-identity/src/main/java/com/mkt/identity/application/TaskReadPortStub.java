package com.mkt.identity.application;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.TaskReadPort;

/** Placeholder until domain-task wires {@link TaskReadPort} (task 28). */
public class TaskReadPortStub implements TaskReadPort {

    @Override
    public InstanceCounts instanceCounts(long userId) {
        return new InstanceCounts(0L, 0L);
    }
}
