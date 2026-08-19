package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.domain.EventBatchCodes;
import com.mkt.tracking.domain.EventSample;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.query.EventLogQuery;
import java.util.ArrayList;
import java.util.Comparator;
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

    @Override
    public long countByQuery(EventLogQuery query) {
        return matches(query).size();
    }

    @Override
    public List<EvtEventLogEntity> listByQuery(EventLogQuery query) {
        List<EvtEventLogEntity> matched = matches(query);
        int from = (int) Math.min(query.offset(), matched.size());
        int to = (int) Math.min(query.offset() + query.limit(), matched.size());
        return new ArrayList<>(matched.subList(from, to));
    }

    public List<EvtEventLogEntity> rows() {
        return rows;
    }

    private List<EvtEventLogEntity> matches(EventLogQuery query) {
        List<EvtEventLogEntity> matched = new ArrayList<>();
        for (EvtEventLogEntity row : rows) {
            if (!EventBatchCodes.matches(row.getEventCode(), row.getEvents(), query.eventCode())) {
                continue;
            }
            if (query.userId() != null && !query.userId().equals(row.getUserId())) {
                continue;
            }
            if (query.source() != null && !query.source().isBlank() && !query.source().equals(row.getSource())) {
                continue;
            }
            if (query.deviceId() != null
                    && !query.deviceId().isBlank()
                    && !query.deviceId().equals(row.getDeviceId())) {
                continue;
            }
            if (query.from() != null
                    && (row.getServerTime() == null || row.getServerTime().isBefore(query.from()))) {
                continue;
            }
            if (query.to() != null && (row.getServerTime() == null || row.getServerTime().isAfter(query.to()))) {
                continue;
            }
            if (!EventSample.include(row.getId(), query.sampleRatioPercent())) {
                continue;
            }
            matched.add(row);
        }
        matched.sort(Comparator.comparing(
                        EvtEventLogEntity::getServerTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(EvtEventLogEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return matched;
    }
}
