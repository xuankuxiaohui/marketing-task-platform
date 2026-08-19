package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.mapper.EvtEventMetadataMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisEventMetadataStore implements EventMetadataStore {

    private final EvtEventMetadataMapper mapper;

    public MybatisEventMetadataStore(EvtEventMetadataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MetadataStatus statusOf(String eventCode) {
        EvtEventMetadataEntity row = mapper.selectByEventCode(eventCode);
        if (row == null || row.getStatus() == null) {
            return MetadataStatus.MISSING;
        }
        if ("DISABLED".equals(row.getStatus())) {
            return MetadataStatus.DISABLED;
        }
        return MetadataStatus.ENABLED;
    }
}
