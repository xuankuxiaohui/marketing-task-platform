package com.mkt.task.application;

import com.mkt.task.response.InstanceEventView;
import java.util.List;

/** Server-side instance timeline (design §6.4 / R14.9): Outbox + evt_event_log. */
public interface InstanceEventStore {

    List<InstanceEventView> listByInstance(long instanceId, Long userId);
}
