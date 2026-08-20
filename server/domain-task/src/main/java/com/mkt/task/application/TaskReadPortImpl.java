package com.mkt.task.application;

import com.mkt.contract.InstanceCounts;
import com.mkt.contract.TaskReadPort;

/** Task-owned read facade (design §2.2.3 / D-13). */
public class TaskReadPortImpl implements TaskReadPort {

    private final TaskInstanceStore instances;

    public TaskReadPortImpl(TaskInstanceStore instances) {
        this.instances = instances;
    }

    @Override
    public InstanceCounts instanceCounts(long userId) {
        return new InstanceCounts(instances.countInProgress(userId), instances.countHistory(userId));
    }
}
