package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.entity.EvtEventLogEntity;

public final class ThrowingEventLogStore implements EventLogStore {

    @Override
    public int insert(EvtEventLogEntity entity) {
        throw new IllegalStateException("track writer down");
    }
}
