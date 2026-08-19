package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import java.util.List;

public interface EventMetadataStore {

    MetadataStatus statusOf(String eventCode);

    EvtEventMetadataEntity getById(long id);

    EvtEventMetadataEntity getByEventCode(String eventCode);

    int insert(EvtEventMetadataEntity entity);

    int update(EvtEventMetadataEntity entity);

    int deleteById(long id);

    long countByQuery(String eventCode, String status);

    List<EvtEventMetadataEntity> listByQuery(String eventCode, String status, long offset, int limit);
}
