package com.mkt.tracking.application;

import com.mkt.tracking.entity.EvtEventLogEntity;

/** Insert-only store for {@code evt_event_log} (RL-12 / R28.2). */
public interface EventLogStore {

    int insert(EvtEventLogEntity entity);
}
