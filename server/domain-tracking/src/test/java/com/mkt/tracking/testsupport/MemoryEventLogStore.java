package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.entity.EvtEventLogEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryEventLogStore implements EventLogStore {

    private final AtomicLong ids = new AtomicLong(1);
    private final List<EvtEventLogEntity> rows = new ArrayList<>();

    @Override
    public int insert(EvtEventLogEntity entity) {
        if (entity.getId() == null) {
            entity.setId(ids.getAndIncrement());
        }
        rows.add(entity);
        return 1;
    }

    public List<EvtEventLogEntity> rows() {
        return rows;
    }
}
