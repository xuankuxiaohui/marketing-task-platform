package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.query.EventLogQuery;
import java.util.List;

public final class ThrowingEventLogStore implements EventLogStore {

    @Override
    public int insert(EvtEventLogEntity entity) {
        throw new IllegalStateException("track writer down");
    }

    @Override
    public long countByQuery(EventLogQuery query) {
        throw new IllegalStateException("track writer down");
    }

    @Override
    public List<EvtEventLogEntity> listByQuery(EventLogQuery query) {
        throw new IllegalStateException("track writer down");
    }
}
