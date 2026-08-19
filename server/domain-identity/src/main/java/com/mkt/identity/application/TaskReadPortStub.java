package com.mkt.identity.application;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.TaskReadPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Placeholder until domain-task wires {@link TaskReadPort} (task 28). */
@Component
@ConditionalOnMissingBean(TaskReadPort.class)
public class TaskReadPortStub implements TaskReadPort {

    @Override
    public InstanceCounts instanceCounts(long userId) {
        return new InstanceCounts(0L, 0L);
    }
}
