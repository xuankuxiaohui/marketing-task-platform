package com.mkt.tracking.application;

import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.query.EventLogQuery;
import java.util.List;

/** Insert-only writes for {@code evt_event_log} (RL-12 / R28.2); selects for debug query (R29.3). */
public interface EventLogStore {

    int insert(EvtEventLogEntity entity);

    long countByQuery(EventLogQuery query);

    List<EvtEventLogEntity> listByQuery(EventLogQuery query);
}
