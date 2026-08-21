package com.mkt.task.testsupport;

import com.mkt.task.application.InstanceEventStore;
import com.mkt.task.response.InstanceEventView;
import java.util.ArrayList;
import java.util.List;

public final class MemoryInstanceEventStore implements InstanceEventStore {

    public final List<InstanceEventView> rows = new ArrayList<>();

    @Override
    public List<InstanceEventView> listByInstance(long instanceId, Long userId) {
        return List.copyOf(rows);
    }
}
