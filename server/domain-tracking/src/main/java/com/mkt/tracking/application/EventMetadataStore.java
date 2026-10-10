package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface EventMetadataStore {

    MetadataStatus statusOf(String eventCode);

    /**
     * Batch metadata status lookup. Missing codes map to {@link MetadataStatus#MISSING}.
     * Null or empty {@code eventCodes} yields an empty map (no store access).
     */
    Map<String, MetadataStatus> statusesOf(Collection<String> eventCodes);

    EvtEventMetadataEntity getById(long id);

    EvtEventMetadataEntity getByEventCode(String eventCode);

    int insert(EvtEventMetadataEntity entity);

    int update(EvtEventMetadataEntity entity);

    int deleteById(long id);

    long countByQuery(String eventCode, String status);

    List<EvtEventMetadataEntity> listByQuery(String eventCode, String status, long offset, int limit);
}
