package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventMetadataStore;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.MetadataStatuses;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryEventMetadataStore implements EventMetadataStore {

    private final AtomicLong ids = new AtomicLong(1);
    private final List<EvtEventMetadataEntity> rows = new ArrayList<>();

    public MemoryEventMetadataStore put(String code, MetadataStatus status) {
        EvtEventMetadataEntity entity = new EvtEventMetadataEntity();
        entity.setId(ids.getAndIncrement());
        entity.setEventCode(code);
        entity.setName(code);
        entity.setStatus(status == MetadataStatus.DISABLED ? MetadataStatuses.DISABLED : MetadataStatuses.ENABLED);
        rows.add(entity);
        return this;
    }

    @Override
    public MetadataStatus statusOf(String eventCode) {
        EvtEventMetadataEntity row = getByEventCode(eventCode);
        if (row == null) {
            return MetadataStatus.MISSING;
        }
        return MetadataStatuses.of(row.getStatus());
    }

    @Override
    public EvtEventMetadataEntity getById(long id) {
        return rows.stream().filter(row -> row.getId() != null && row.getId() == id).findFirst().orElse(null);
    }

    @Override
    public EvtEventMetadataEntity getByEventCode(String eventCode) {
        if (eventCode == null) {
            return null;
        }
        return rows.stream().filter(row -> eventCode.equals(row.getEventCode())).findFirst().orElse(null);
    }

    @Override
    public int insert(EvtEventMetadataEntity entity) {
        if (entity.getId() == null) {
            entity.setId(ids.getAndIncrement());
        }
        rows.add(entity);
        return 1;
    }

    @Override
    public int update(EvtEventMetadataEntity entity) {
        for (int i = 0; i < rows.size(); i++) {
            if (entity.getId() != null && entity.getId().equals(rows.get(i).getId())) {
                rows.set(i, entity);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteById(long id) {
        return rows.removeIf(row -> row.getId() != null && row.getId() == id) ? 1 : 0;
    }

    @Override
    public long countByQuery(String eventCode, String status) {
        return matches(eventCode, status).size();
    }

    @Override
    public List<EvtEventMetadataEntity> listByQuery(String eventCode, String status, long offset, int limit) {
        List<EvtEventMetadataEntity> matched = matches(eventCode, status);
        matched.sort(Comparator.comparing(EvtEventMetadataEntity::getId));
        int from = (int) Math.min(offset, matched.size());
        int to = (int) Math.min(offset + limit, matched.size());
        return new ArrayList<>(matched.subList(from, to));
    }

    private List<EvtEventMetadataEntity> matches(String eventCode, String status) {
        List<EvtEventMetadataEntity> matched = new ArrayList<>();
        for (EvtEventMetadataEntity row : rows) {
            if (eventCode != null && !eventCode.isBlank() && !eventCode.equals(row.getEventCode())) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(row.getStatus())) {
                continue;
            }
            matched.add(row);
        }
        return matched;
    }
}
